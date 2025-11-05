import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import dotenv from 'dotenv';
import { StreamChat } from 'stream-chat';
import admin from 'firebase-admin';

dotenv.config();

const {
  PORT = 8080,
  STREAM_KEY,
  STREAM_SECRET,
  ALLOWED_ORIGINS = '',
  TOKEN_SERVER_SECRET,
  FIREBASE_SERVICE_ACCOUNT_JSON_BASE64,
  FIREBASE_PROJECT_ID
} = process.env;

if (!STREAM_KEY || !STREAM_SECRET) {
  console.error('Missing STREAM_KEY or STREAM_SECRET in .env');
  process.exit(1);
}

const serverClient = StreamChat.getInstance(STREAM_KEY, STREAM_SECRET);

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
    console.log('Firebase Admin initialized via base64 service account');
  } else if (process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    admin.initializeApp();
    firebaseEnabled = true;
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
  res.json({ ok: true, key: !!STREAM_KEY });
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

    // Create Stream channel with event type
    const channel = serverClient.channel('event', eventId, {
      name: eventName,
      event_admin: adminUserId,
      event_description: description || '',
      event_date: eventDate || null,
      event_cover_image: coverImage || '',
      join_link: joinLink,
      created_by: { id: adminUserId },
      members: [adminUserId], // Admin is the first member
    });

    await channel.create();

    // Make admin a moderator (for permissions)
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

    const channel = serverClient.channel('event', eventId);
    
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
    const channel = serverClient.channel('event', eventId);
    
    const response = await channel.query();
    
    res.json({
      success: true,
      event: {
        id: response.channel.id,
        name: response.channel.data.name,
        description: response.channel.data.event_description,
        adminUserId: response.channel.data.event_admin,
        eventDate: response.channel.data.event_date,
        coverImage: response.channel.data.event_cover_image,
        joinLink: response.channel.data.join_link,
        memberCount: response.members.length,
        createdAt: response.channel.created_at
      }
    });
  } catch (e) {
    console.error('Error fetching event:', e);
    res.status(404).json({ error: 'Event not found' });
  }
});

// ========== MESSAGE VALIDATION WEBHOOK ==========

// Webhook to validate messages (called before message is sent)
app.post('/webhook/message', async (req, res) => {
  try {
    const { type, message, channel_type, channel_id } = req.body;

    // Only validate for event channels
    if (channel_type !== 'event') {
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

    // Get channel to check admin
    const channel = serverClient.channel('event', channel_id);
    const channelData = await channel.query();
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
});