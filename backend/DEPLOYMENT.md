# Google Cloud Run + Redis Deployment Guide

This document describes how to deploy the **UNO Royale 2-Player Multiplayer Backend** to **Google Cloud Run** with **Google Cloud Memorystore for Redis**.

---

## 1. Architecture Overview
- **Service**: Google Cloud Run (Containerized Node.js + TypeScript + Socket.IO).
- **In-Memory Store & Adapter**: Google Cloud Memorystore for Redis (or private Upstash Redis).
- **Communication**: WebSocket transport with HTTP long-polling fallback, session reconnection tokens, and authoritative server-side state.
- **Security**: Private VPC network between Cloud Run and Memorystore (Redis is NOT exposed publicly).

---

## 2. Prerequisites
1. [Google Cloud SDK (`gcloud`)](https://cloud.google.com/sdk/docs/install) installed and authenticated.
2. An active Google Cloud Project with Billing enabled.
3. Enabled Google Cloud APIs:
```bash
gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  cloudbuild.googleapis.com \
  redis.googleapis.com \
  vpcaccess.googleapis.com
```

---

## 3. Provision Google Cloud Memorystore for Redis

```bash
# 1. Create a VPC Serverless Connector (allows Cloud Run to securely reach Redis)
gcloud compute networks vpc-access connectors create uno-vpc-connector \
  --region=us-central1 \
  --range=10.8.0.0/28

# 2. Create the Memorystore Redis Instance (1 GB Tier)
gcloud redis instances create uno-redis \
  --size=1 \
  --region=us-central1 \
  --zone=us-central1-a \
  --redis-version=redis_7_0

# 3. Retrieve the Private Redis IP
gcloud redis instances describe uno-redis --region=us-central1 --format="value(host)"
# Example output: 10.0.0.3
```

---

## 4. Build and Deploy to Cloud Run

From the `/backend` folder:

```bash
# 1. Build and submit container image using Cloud Build
gcloud builds submit --tag gcr.io/$(gcloud config get-value project)/uno-multiplayer-backend

# 2. Deploy to Cloud Run with VPC Connector and Redis environment variables
gcloud run deploy uno-multiplayer-backend \
  --image gcr.io/$(gcloud config get-value project)/uno-multiplayer-backend \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --vpc-connector uno-vpc-connector \
  --set-env-vars REDIS_HOST="10.0.0.3",REDIS_PORT="6379" \
  --min-instances=1 \
  --max-instances=10 \
  --port=8080
```

---

## 5. Verify Health Endpoint
Once deployed, Cloud Run will print your Service URL (e.g. `https://uno-multiplayer-backend-xyz.a.run.app`).

Verify with curl:
```bash
curl https://uno-multiplayer-backend-xyz.a.run.app/health
```

Expected JSON response:
```json
{
  "status": "ok",
  "service": "uno-royale-multiplayer-backend",
  "timestamp": "2026-11-08T08:45:00.000Z",
  "uptimeSeconds": 42,
  "activeConnections": 0
}
```

---

## 6. Configure Android App
In your Android app, set your backend URL in `MultiplayerRepository.kt` or `BuildConfig.BACKEND_URL`:
```kotlin
const val DEFAULT_BACKEND_URL = "https://uno-multiplayer-backend-xyz.a.run.app"
```
The app will connect seamlessly over secure WebSockets (`wss://`).
