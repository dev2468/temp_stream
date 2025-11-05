import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import dotenv from 'dotenv';
import { StreamChat } from 'stream-chat';
import admin from 'firebase-admin';
import { GoogleGenerativeAI } from '@google/generative-ai';

dotenv.config();

const {
  PORT = 8080,
  STREAM_KEY,
  STREAM_SECRET,
  ALLOWED_ORIGINS = '',
  TOKEN_SERVER_SECRET,
  FIREBASE_SERVICE_ACCOUNT_JSON_BASE64,
  FIREBASE_PROJECT_ID,
  GEMINI_API_KEY
} = process.env;

if (!STREAM_KEY || !STREAM_SECRET) {
  console.error('Missing STREAM_KEY or STREAM_SECRET in .env');
  process.exit(1);
}

const serverClient = StreamChat.getInstance(STREAM_KEY, STREAM_SECRET);

// Initialize Gemini AI
let genAI = null;
let geminiModel = null;
if (GEMINI_API_KEY) {
  try {
    genAI = new GoogleGenerativeAI(GEMINI_API_KEY);
    geminiModel = genAI.getGenerativeModel({ model: 'gemini-1.5-flash' });
    console.log('Gemini AI initialized successfully');
  } catch (e) {
    console.warn('Failed to initialize Gemini AI:', e.message);
  }
}

// Initialize Firestore for bot memory (only if Firebase is enabled)
let db = null;

const app = express();
app.use(helmet());
app.use(express.json());

// CORS — allow local Android emulator and optional web origins
const defaultOrigins = [
  'http://localhost:3000',
  'http://localhost:5173',
  'http://127.0.0.1:5173'
];
const extraOrigins = ALLOWED_ORIGINS.split(',').map(s => s.trim()).filter(Boolean);
const allowedOrigins = [...defaultOrigins, ...extraOrigins];
app.use(cors({
  origin: (origin, cb) => {
    if (!origin) return cb(null, true); // mobile apps/CLI
    if (allowedOrigins.includes(origin)) return cb(null, true);
    return cb(new Error('Not allowed by CORS'));
  },
  credentials: false
}));

// Basic rate limiting
const limiter = rateLimit({ windowMs: 60 * 1000, limit: 120 });
app.use(limiter);

// Optional shared-secret auth for dev/prod hardening
if (TOKEN_SERVER_SECRET) {
  app.use((req, res, next) => {
    const provided = req.get('X-Token-Server-Secret');
    if (provided !== TOKEN_SERVER_SECRET) {
      return res.status(401).json({ error: 'Unauthorized' });
    }
    next();
  });
}

// Firebase ID token verification (conditional). Enabled when credentials are provided.
let firebaseEnabled = false;
try {
  if (FIREBASE_SERVICE_ACCOUNT_JSON_BASE64) {
    const json = JSON.parse(Buffer.from(FIREBASE_SERVICE_ACCOUNT_JSON_BASE64, 'base64').toString('utf8'));
    admin.initializeApp({ credential: admin.credential.cert(json), projectId: FIREBASE_PROJECT_ID || json.project_id });
    firebaseEnabled = true;
    db = admin.firestore(); // Initialize Firestore for bot memory
    console.log('Firebase Admin initialized via base64 service account');
  } else if (process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    admin.initializeApp();
    firebaseEnabled = true;
    db = admin.firestore(); // Initialize Firestore for bot memory
    console.log('Firebase Admin initialized via GOOGLE_APPLICATION_CREDENTIALS');
  }
} catch (e) {
  console.warn('Failed to initialize Firebase Admin; Firebase auth disabled:', e.message);
}

async function verifyFirebaseIdToken(req, res, next) {
  if (!firebaseEnabled) return next();
  try {
    const auth = req.get('Authorization') || '';
    const token = auth.startsWith('Bearer ') ? auth.slice(7) : '';
    if (!token) return res.status(401).json({ error: 'Missing Authorization Bearer token' });

    const decoded = await admin.auth().verifyIdToken(token, false); // don't check revocation for dev
    console.log('Firebase token verified for uid:', decoded.uid);
    req.firebaseUser = {
      id: decoded.uid,
      email: decoded.email,
      name: decoded.name,
      picture: decoded.picture
    };
    if (!req.firebaseUser.id) return res.status(401).json({ error: 'Invalid token: missing uid' });
    return next();
  } catch (e) {
    console.error('Firebase ID token verification failed:', e.message);
    const details = process.env.NODE_ENV === 'production' ? undefined : e.message;
    return res.status(401).json({ error: 'Invalid token', ...(details && { detail: details }) });
  }
}

app.get('/health', (_req, res) => {
  // Expose only safe config for quick diagnostics. STREAM_KEY is public (also shipped in client apps).
  res.json({
    ok: true,
    stream_key: STREAM_KEY,
    firebase_enabled: firebaseEnabled,
    firebase_project_id: FIREBASE_PROJECT_ID || undefined
  });
});

// Secondary health endpoint with a different path to avoid any caches and to help identify new deployments
app.get('/healthz', (_req, res) => {
  res.json({
    ok: true,
    stream_key: STREAM_KEY,
    firebase_enabled: firebaseEnabled,
    firebase_project_id: FIREBASE_PROJECT_ID || undefined,
    started_at: new Date().toISOString()
  });
});

// Issue a token and upsert the user so channel creation works
// GET /token?user_id=john&name=John%20Doe&image=https://...
// When Firebase is configured, derives user_id from the verified ID token's uid
app.get('/token', verifyFirebaseIdToken, async (req, res) => {
  try {
    const derivedId = req.firebaseUser?.id;
    const userId = String((derivedId || req.query.user_id || '')).trim();
    if (!userId) return res.status(400).json({ error: 'Missing user_id' });

    const name = String(req.query.name || req.firebaseUser?.name || '').trim();
    const image = String(req.query.image || req.firebaseUser?.picture || '').trim();

    // Upsert user metadata (safe to call repeatedly)
    await serverClient.upsertUser({ id: userId, ...(name && { name }), ...(image && { image }) });

    const token = serverClient.createToken(userId);
    res.json({ token });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: 'Failed to issue token' });
  }
});

// ========== EVENT ENDPOINTS ==========

// Create a new event
app.post('/events/create', verifyFirebaseIdToken, async (req, res) => {
  try {
    const { eventName, description, eventDate, coverImage } = req.body;
    const adminUserId = req.firebaseUser?.id || req.body.adminUserId;
    
    if (!adminUserId || !eventName) {
      return res.status(400).json({ error: 'Missing required fields: adminUserId, eventName' });
    }

    // Generate unique event ID and join link
    const eventId = `event-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    const joinLink = `temp://event/${eventId}`;

    // Create Stream channel with messaging type (using existing type)
    const channel = serverClient.channel('messaging', eventId, {
      name: eventName,
      event_admin: adminUserId,
      event_description: description || '',
      event_date: eventDate || null,
      event_cover_image: coverImage || '',
      join_link: joinLink,
      created_by: { id: adminUserId },
      is_event_channel: true, // Mark as event channel
    });

    // Create channel and add admin as member first
    await channel.create();
    await channel.addMembers([adminUserId]);

    // Then make admin a moderator (for permissions)
    await channel.addModerators([adminUserId]);

    res.json({
      success: true,
      eventId,
      joinLink,
      channelId: channel.id,
      channelCid: channel.cid
    });
  } catch (e) {
    console.error('Error creating event:', e);
    res.status(500).json({ error: 'Failed to create event', detail: e.message });
  }
});

// Join an event via link
app.post('/events/join', verifyFirebaseIdToken, async (req, res) => {
  try {
    const { eventId } = req.body;
    const userId = req.firebaseUser?.id || req.body.userId;
    
    if (!eventId || !userId) {
      return res.status(400).json({ error: 'Missing eventId or userId' });
    }

    const channel = serverClient.channel('messaging', eventId);
    
    // Check if channel exists
    try {
      await channel.watch();
    } catch (e) {
      return res.status(404).json({ error: 'Event not found' });
    }

    // Add user as member (read-only by default)
    await channel.addMembers([userId], { hide_history: false });

    res.json({
      success: true,
      channelId: channel.id,
      channelCid: channel.cid
    });
  } catch (e) {
    console.error('Error joining event:', e);
    res.status(500).json({ error: 'Failed to join event', detail: e.message });
  }
});

// Get event details
app.get('/events/:eventId', verifyFirebaseIdToken, async (req, res) => {
  try {
    const { eventId } = req.params;

    // Use queryChannels for a robust lookup by ID and type
    const channels = await serverClient.queryChannels(
      { type: 'messaging', id: { $eq: eventId } },
      [],
      { state: true, watch: false, limit: 1 }
    );

    if (!channels || channels.length === 0) {
      return res.status(404).json({ error: 'Event not found' });
    }

    const ch = channels[0];
    const data = ch.data || {};
    const memberCount = typeof ch.member_count === 'number'
      ? ch.member_count
      : Array.isArray(ch.members) ? ch.members.length : 0;

    res.json({
      success: true,
      event: {
        id: ch.id,
        name: data.name,
        description: data.event_description,
        adminUserId: data.event_admin,
        eventDate: data.event_date,
        coverImage: data.event_cover_image,
        joinLink: data.join_link,
        memberCount,
        createdAt: ch.created_at
      }
    });
  } catch (e) {
    console.error('Error fetching event:', e);
    res.status(500).json({ error: 'Failed to fetch event', detail: e.message });
  }
});

// ========== AI CHATBOT ENDPOINT ==========

// Chat with AI bot (mention-based: @bot <message>)
app.post('/chat/bot', verifyFirebaseIdToken, async (req, res) => {
  try {
    const { message, channelId, channelType = 'messaging' } = req.body;
    const userId = req.firebaseUser?.id || req.body.userId;

    if (!userId || !message || !channelId) {
      return res.status(400).json({ error: 'Missing required fields: userId, message, channelId' });
    }

    if (!geminiModel) {
      return res.status(503).json({ error: 'AI bot is not configured. Please set GEMINI_API_KEY in environment.' });
    }

    // Get or create bot user
    const botUserId = 'ai-assistant';
    try {
      await serverClient.upsertUser({
        id: botUserId,
        name: 'AI Study Bot',
        image: 'https://cdn-icons-png.flaticon.com/512/4712/4712027.png', // Robot icon
        role: 'user'
      });
    } catch (e) {
      console.warn('Bot user upsert warning:', e.message);
    }

    // Fetch conversation history from Firestore (if available)
    let conversationHistory = [];
    if (db) {
      try {
        const historyRef = db.collection('chat_memory').doc(userId);
        const historyDoc = await historyRef.get();
        if (historyDoc.exists) {
          conversationHistory = historyDoc.data().history || [];
        }
      } catch (e) {
        console.warn('Failed to fetch conversation history:', e.message);
      }
    }

    // Build conversation context for Gemini
    // Keep last 10 messages to avoid token limits
    const recentHistory = conversationHistory.slice(-10);
    
    // System prompt
    const systemPrompt = `You are an AI Study Assistant helping students learn. Be helpful, encouraging, and concise. Keep responses under 200 words unless asked for detailed explanations.`;
    
    // Build chat history format for Gemini
    const chatHistory = [
      { role: 'user', parts: [{ text: systemPrompt }] },
      { role: 'model', parts: [{ text: 'Understood! I\'m here to help you learn. Ask me anything!' }] }
    ];
    
    // Add previous conversation
    for (const msg of recentHistory) {
      chatHistory.push({
        role: msg.role === 'assistant' ? 'model' : 'user',
        parts: [{ text: msg.content }]
      });
    }

    // Start chat session with history
    const chat = geminiModel.startChat({
      history: chatHistory,
      generationConfig: {
        maxOutputTokens: 500,
        temperature: 0.7,
      }
    });

    // Send user's message
    const result = await chat.sendMessage(message);
    const botReply = result.response.text();

    // Save updated conversation history to Firestore
    if (db) {
      try {
        conversationHistory.push({ role: 'user', content: message });
        conversationHistory.push({ role: 'assistant', content: botReply });
        
        // Keep only last 20 messages to prevent unlimited growth
        const trimmedHistory = conversationHistory.slice(-20);
        
        const historyRef = db.collection('chat_memory').doc(userId);
        await historyRef.set({ history: trimmedHistory, updatedAt: new Date() });
      } catch (e) {
        console.warn('Failed to save conversation history:', e.message);
      }
    }

    // Send bot reply to Stream Chat channel
    try {
      const channel = serverClient.channel(channelType, channelId);
      await channel.sendMessage({
        text: botReply,
        user_id: botUserId
      });
    } catch (e) {
      console.error('Failed to send bot message to channel:', e.message);
      // Still return the reply even if posting to channel fails
    }

    res.json({
      success: true,
      reply: botReply
    });
  } catch (e) {
    console.error('Error in bot chat:', e);
    res.status(500).json({ error: 'Failed to process bot chat', detail: e.message });
  }
});

// ========== MESSAGE VALIDATION WEBHOOK ==========

// Webhook to validate messages (called before message is sent)
app.post('/webhook/message', async (req, res) => {
  try {
    const { type, message, channel_type, channel_id } = req.body;

    // Only validate for event channels (check custom flag)
    if (channel_type !== 'messaging') {
      return res.status(200).json({ message: 'allowed' });
    }

    // Only validate on message.new events
    if (type !== 'message.new') {
      return res.status(200).json({ message: 'allowed' });
    }

    const senderId = message?.user?.id;
    if (!senderId) {
      return res.status(403).json({ 
        error: 'No sender ID',
        message: 'rejected'
      });
    }

    // Get channel to check if it's an event channel and check admin
    const channel = serverClient.channel('messaging', channel_id);
    const channelData = await channel.query();
    
    // Only validate if this is an event channel
    if (!channelData.channel.data.is_event_channel) {
      return res.status(200).json({ message: 'allowed' });
    }
    
    const eventAdmin = channelData.channel.data.event_admin;

    // Check if sender is the admin
    if (senderId !== eventAdmin) {
      return res.status(403).json({
        error: 'Only the event organizer can send messages',
        message: 'rejected'
      });
    }

    res.status(200).json({ message: 'allowed' });
  } catch (e) {
    console.error('Webhook error:', e);
    // On error, allow the message to avoid blocking legitimate messages
    res.status(200).json({ message: 'allowed' });
  }
});

app.listen(PORT, () => {
  console.log(`Token server listening on http://localhost:${PORT}`);
  try {
    const masked = (STREAM_KEY || '').replace(/.(?=.{4})/g, '*');
    console.log(`[diag] Using Stream API key: ${masked}`);
    console.log(`[diag] Firebase enabled: ${firebaseEnabled}${FIREBASE_PROJECT_ID ? ` (project: ${FIREBASE_PROJECT_ID})` : ''}`);
  } catch {}
});