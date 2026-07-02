# Backend Deployment Guide - Pulse Pairing System

## Pre-Deployment Checklist

- [ ] Node.js 22 installed
- [ ] TypeScript configured
- [ ] PostgreSQL database running
- [ ] Firebase Admin SDK configured
- [ ] Environment variables set (`.env` file)
- [ ] Git ready for commits

---

## Environment Variables (.env)

```env
# Database
DATABASE_URL=postgresql://user:password@localhost:5432/pulse_db

# JWT
JWT_SECRET=your-super-secret-key-change-in-production-use-32chars-min

# Firebase (for FCM)
GOOGLE_APPLICATION_CREDENTIALS=/path/to/serviceAccountKey.json

# Server
PORT=3000
NODE_ENV=production

# API
API_URL=https://pluse-app-backend.onrender.com
FRONTEND_URL=https://pulse.app
```

---

## Database Setup

### Step 1: Create Database
```bash
createdb pulse_db
```

### Step 2: Run Schema

```sql
-- Connect to database
psql -U postgres -d pulse_db -f src/db/schema.sql

-- Verify tables
\dt
-- Should see: couples, invites, signals, users
```

---

## File Changes Required

### 1. **New Files to Create**

```
src/routes/couple-code.ts       # NEW - Couple code endpoints
src/routes/signal.ts             # NEW - Signal/vibration endpoints
src/utils/jwt.ts                 # NEW - JWT verification utility
```

### 2. **Files to Update**

```
src/app.ts                       # Register new routes
src/websocket/index.ts           # Fix JWT verification
src/db/schema.sql                # Add signals table
package.json                      # Add jwt dependency if missing
```

---

## Installation & Deployment

### Step 1: Install Dependencies

```bash
npm install
# Should already have these, but verify:
npm list fastify
npm list jsonwebtoken
npm list pg
```

If `jsonwebtoken` is missing:
```bash
npm install jsonwebtoken
npm install --save-dev @types/jsonwebtoken
```

### Step 2: Build TypeScript

```bash
npx tsc --outDir dist
# Should compile without errors
```

### Step 3: Test Locally

```bash
npm start
# or
NODE_ENV=development npm start
```

Should see:
```
Server listening on port 3000
```

### Step 4: Deploy to Production

#### **Option A: Render**
```bash
# Push to GitHub
git add .
git commit -m "Fix: Implement couple pairing and signal system"
git push origin main

# Render auto-deploys on push
# Check: https://dashboard.render.com
```

#### **Option B: Heroku**
```bash
heroku login
git remote add heroku https://git.heroku.com/your-app.git
git push heroku main
```

#### **Option C: Docker**
```bash
docker build -t pulse-backend .
docker run -p 3000:3000 -e DATABASE_URL="..." pulse-backend
```

---

## Testing Deployment

### Health Check
```bash
curl https://pluse-app-backend.onrender.com/health
# Should respond or at least not 404
```

### WebSocket Test
```bash
# Install wscat if needed
npm install -g wscat

# Generate test JWT
node -e "const jwt = require('jsonwebtoken'); console.log(jwt.sign({id: 'test-user', email: 'test@example.com'}, 'test-secret', {expiresIn: '1h'}))"

# Connect
wscat -c 'wss://pluse-app-backend.onrender.com/ws?token=YOUR_JWT_TOKEN'
# Should connect successfully
```

### Create Couple Code
```bash
curl -X POST https://pluse-app-backend.onrender.com/couple/code/create \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json"

# Response should include code and couple_id
```

---

## Database Verification

### Connect to Production Database

```bash
psql $DATABASE_URL

# List tables
\dt

# Check couples table
SELECT * FROM couples LIMIT 5;

# Check invites table
SELECT id, short_code, used, expires_at FROM invites LIMIT 5;

# Check signals table
SELECT * FROM signals LIMIT 5;

# Check indexes
\di
```

---

## Monitoring & Logs

### Render Logs
```bash
render logs --service pulse-backend
```

### Error Monitoring
Watch for:
- `INVALID_OR_EXPIRED_CODE`
- `COUPLE_ALREADY_FULL`
- `Failed to broadcast partner connection`
- `WS Error`

### Real-time Monitoring

Enable debug logging:
```typescript
// In app.ts
const app = fastify({ 
  logger: {
    level: 'debug'
  } 
});
```

---

## Troubleshooting

### Issue: WebSocket Connection Fails

**Cause:** JWT verification failing

**Solution:**
```bash
# Verify JWT_SECRET matches client
echo $JWT_SECRET

# Test JWT generation locally
node -e "
const jwt = require('jsonwebtoken');
const token = jwt.sign({id: 'test', email: 'test@test.com'}, process.env.JWT_SECRET);
console.log(token);
"
```

### Issue: Couple Code Not Working

**Cause:** Database not initialized

**Solution:**
```bash
# Re-run schema
psql $DATABASE_URL -f src/db/schema.sql

# Verify signals table exists
psql $DATABASE_URL -c "\\dt signals"
```

### Issue: Deep Links Not Firing Backend

**Cause:** Token not passed correctly

**Solution:**
1. Check Android deep link URL format: `https://pulse.app/invite/{64-char-hex-token}`
2. Verify token isn't url-encoded incorrectly
3. Test endpoint manually:
```bash
curl -X POST https://pluse-app-backend.onrender.com/couple/join/your-token-here \
  -H "Authorization: Bearer JWT_TOKEN"
```

### Issue: FCM Not Sending

**Cause:** Firebase not configured or no device token

**Solution:**
```bash
# Verify Firebase initialized
echo $GOOGLE_APPLICATION_CREDENTIALS

# Check file exists
ls $GOOGLE_APPLICATION_CREDENTIALS
```

---

## Performance Optimization

### Rate Limiting

Currently configured to 100 requests per minute globally. Adjust in `app.ts`:

```typescript
app.register(rateLimit, {
  max: 100,  // Increase for production
  timeWindow: '1 minute'
});
```

### Database Connection Pool

Configured in `db/pool.ts`:

```typescript
const pool = new Pool({
  max: 20,                      // Max connections
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 2000
});
```

For high load, increase `max`:
```typescript
max: 50  // Handle more concurrent requests
```

### WebSocket Scaling

Consider Redis for multi-instance deployments:

```typescript
// Example with Redis adapter (future enhancement)
import { createAdapter } from '@socket.io/redis-adapter';
```

---

## Security Hardening

### 1. Change JWT_SECRET

**CRITICAL:** Generate a strong secret:

```bash
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

Set in production:
```bash
heroku config:set JWT_SECRET=your-generated-secret
# or in Render environment variables
```

### 2. Enable HTTPS Only

```typescript
// In app.ts or server.ts
const options = {
  https: true  // Force HTTPS
};
```

### 3. Add CORS

```typescript
import cors from '@fastify/cors';

app.register(cors, {
  origin: ['https://pulse.app'],
  credentials: true
});
```

### 4. Validate Input

```typescript
// Add schema validation to all endpoints
const schema = {
  body: {
    type: 'object',
    required: ['couple_id', 'signal_type'],
    properties: {
      couple_id: { type: 'string', format: 'uuid' },
      signal_type: { type: 'string', enum: ['vibrate', 'heart', 'kiss'] }
    }
  }
};
```

---

## Backup & Recovery

### Backup Database

```bash
# Full backup
pg_dump $DATABASE_URL > pulse_backup.sql

# Compressed backup
pg_dump $DATABASE_URL | gzip > pulse_backup.sql.gz
```

### Restore Database

```bash
psql $DATABASE_URL < pulse_backup.sql
```

### Point-in-Time Recovery

Enable WAL (Write-Ahead Logging):
```sql
-- In PostgreSQL config
max_wal_senders = 10
wal_level = replica
```

---

## Maintenance

### Weekly
- [ ] Check disk usage
- [ ] Monitor error logs
- [ ] Verify database size
- [ ] Test WebSocket connections

### Monthly
- [ ] Update dependencies (`npm update`)
- [ ] Review JWT expiration
- [ ] Analyze API performance
- [ ] Clean up expired codes/invites

### Cleanup Script

```sql
-- Remove expired invites
DELETE FROM invites 
WHERE expires_at < NOW() AND used = false;

-- Remove old signals
DELETE FROM signals 
WHERE created_at < NOW() - INTERVAL '30 days';
```

---

## Rollback Plan

### Revert to Previous Version

```bash
git log --oneline | head -10
git revert HEAD
git push
```

### Database Rollback

```bash
# From backup
psql $DATABASE_URL < pulse_backup.sql

# Or manually
-- Add back removed columns
ALTER TABLE couples ADD COLUMN archived BOOLEAN DEFAULT false;
```

---

## Post-Deployment Verification

Run this checklist after deploying:

```bash
#!/bin/bash

echo "1. Testing couple code creation..."
curl -X POST $API_URL/couple/code/create \
  -H "Authorization: Bearer $TEST_JWT"

echo "2. Testing WebSocket connection..."
wscat -c "wss://${API_URL#https://}/ws?token=$TEST_JWT"

echo "3. Testing database connection..."
curl $API_URL/health

echo "4. Checking memory usage..."
curl $API_URL/metrics  # if metrics endpoint exists

echo "✅ Deployment verification complete!"
```

---

## Scaling Strategy

### For 100s of concurrent couples:
- ✅ Current setup handles fine
- Keep PostgreSQL connection pool at 20-30

### For 1000s of concurrent couples:
- [ ] Add Redis for WebSocket client lookup
- [ ] Implement message queue for signals
- [ ] Consider horizontal scaling with load balancer
- [ ] Use managed database (AWS RDS, Google Cloud SQL)

### For 10,000+ concurrent couples:
- [ ] Implement dedicated signal service (using NATS, RabbitMQ)
- [ ] Separate read replicas for queries
- [ ] CDN for assets
- [ ] Rate limiting by user/couple

---

## Documentation for Team

### API Documentation

Deploy with auto-generated docs:

```bash
npm install @fastify/swagger @fastify/swagger-ui

# Add to app.ts
app.register(require('@fastify/swagger'), { ... });
```

Then access: `https://api.pulse.app/documentation`

### Runbook

Save this as `RUNBOOK.md` in your ops folder

