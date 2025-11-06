# Stream Chat Token Server

Minimal Node.js server that issues Stream Chat user tokens, manages events, and provides AI chatbot integration using Gemini AI.

## Prereqs
- Node.js 18+
- Stream Chat API Key and Secret (from Stream Dashboard → Chat → Overview)
- Firebase project (for authentication and Firestore)
- Gemini API Key (from https://aistudio.google.com/app/apikey) - optional, for AI bot

## Setup (Windows PowerShell)
```powershell
cd backend/token-server
copy .env.example .env
# Edit .env and set:
#  - STREAM_KEY and STREAM_SECRET
#  - FIREBASE_SERVICE_ACCOUNT_JSON_BASE64 and FIREBASE_PROJECT_ID
#  - GEMINI_API_KEY (for AI chatbot feature)
#  - Optional: TOKEN_SERVER_SECRET
npm install
npm run start
```

Server runs on http://localhost:8080 by default.

## Endpoints
- GET /health → { ok: true, stream_key: "<your-stream-api-key>", firebase_enabled: true|false, firebase_project_id?: string }
- GET /healthz → Extended health check with timestamp
- GET /token?user_id=john&name=John%20Doe&image=https://... → { token: "..." }
  - Also upserts the user so they exist for channel creation.
  - Requires `Authorization: Bearer <firebase_id_token>` when Firebase is configured
  
### Event Management
- POST /events/create → Create a new event channel
- POST /events/join → Join an event via event ID
- GET /events/:eventId → Get event details

### AI Chatbot
- POST /chat/bot → Send message to AI bot
  - Requires: { message, channelId, channelType, userId }
  - Returns: { success: true, reply: "..." }
  - Bot maintains conversation history per user in Firestore
  - Bot automatically posts response to the Stream channel

### Webhooks
- POST /webhook/message → Message validation webhook (enforces admin-only posting for events)

## AI Chatbot Features
- **Mention-based**: Send `@bot <your message>` in any channel
- **Memory**: Remembers last 10 messages per user (stored in Firestore)
- **Context-aware**: Uses Gemini 1.5 Flash for fast, intelligent responses
- **Study Assistant**: Optimized to help students learn
- **Automatic responses**: Bot replies appear as normal Stream Chat messages
- **Model**: Uses `gemini-1.5-flash` (latest version, fast and efficient)

## Security notes
- Do NOT expose STREAM_SECRET to clients.
- Add auth (API key, Firebase auth, etc.) before production.
- Restrict origins with ALLOWED_ORIGINS if serving a web app.

### Optional shared-secret check
If you set `TOKEN_SERVER_SECRET` in `.env`, the server will require the header `X-Token-Server-Secret: <value>` on every request. Keep this value out of client apps that you don't control.

### Supabase JWT verification
Set these `.env` keys to enable verification:
- `SUPABASE_URL=https://<your-project-ref>.supabase.co`
- Optional: `SUPABASE_JWKS_URL` (defaults to `${SUPABASE_URL}/auth/v1/jwks`)
- Optional: `SUPABASE_EXPECTED_AUD` (often `authenticated`)

Then send `Authorization: Bearer <supabase_access_token>` to `/token`. The server verifies the token against Supabase JWKS and uses `sub` as the Stream user id.

### Firebase ID token verification
Set one of these to enable verification:
- `GOOGLE_APPLICATION_CREDENTIALS=C:\\path\\to\\service-account.json` (file path)
- or `FIREBASE_SERVICE_ACCOUNT_JSON_BASE64=<base64-encoded service account JSON>`

Optional: `FIREBASE_PROJECT_ID` if not present in the service account.

Clients should send `Authorization: Bearer <firebase_id_token>` to `/token`.
