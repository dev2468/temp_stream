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

app.listen(PORT, () => {
  console.log(`Token server listening on http://localhost:${PORT}`);
});
