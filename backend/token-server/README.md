# Stream Chat Token Server

Minimal Node.js server that issues Stream Chat user tokens and upserts users so channel creation works without 400 errors.

## Prereqs
- Node.js 18+
- Stream Chat API Key and Secret (from Stream Dashboard → Chat → Overview)

## Setup (Windows PowerShell)
```powershell
cd backend/token-server
copy .env.example .env
# Edit .env and set STREAM_KEY and STREAM_SECRET
# Optional (recommended later): TOKEN_SERVER_SECRET
# Optional auth providers:
#  - Supabase: set SUPABASE_URL (and optionally SUPABASE_EXPECTED_AUD)
#  - Firebase: set GOOGLE_APPLICATION_CREDENTIALS to a service account json path
#               or set FIREBASE_SERVICE_ACCOUNT_JSON_BASE64 with base64-encoded JSON
npm install
npm run start
```

Server runs on http://localhost:8080 by default.

## Endpoints
- GET /health → { ok: true }
- GET /token?user_id=john&name=John%20Doe&image=https://... → { token: "..." }
  - Also upserts the user so they exist for channel creation.
  - If Supabase is configured, requires `Authorization: Bearer <supabase_access_token>` and derives `user_id` from `sub`.
  - If Firebase is configured, requires `Authorization: Bearer <firebase_id_token>` and derives `user_id` from `uid`.

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
