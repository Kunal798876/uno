import Redis from 'ioredis';

let pubClient: Redis | null = null;
let subClient: Redis | null = null;
let redisAvailable = false;

// In-memory fallback map if Redis is not running locally or during development
const memoryStore = new Map<string, { value: string; expiry?: number }>();

export function getRedisConfig(): { host: string; port: number; password?: string; url?: string } {
  if (process.env.REDIS_URL) {
    return { url: process.env.REDIS_URL, host: '', port: 6379 };
  }
  return {
    host: process.env.REDIS_HOST || '127.0.0.1',
    port: parseInt(process.env.REDIS_PORT || '6379', 10),
    password: process.env.REDIS_PASSWORD || undefined,
  };
}

export function initRedisClients(): { pub: Redis | null; sub: Redis | null; isRedisReady: () => boolean } {
  // Only connect to Redis if explicitly configured via environment variable
  if (!process.env.REDIS_URL && !process.env.REDIS_HOST) {
    console.log('[Redis] No external REDIS_URL or REDIS_HOST provided. Using built-in in-memory store.');
    redisAvailable = false;
    return {
      pub: null,
      sub: null,
      isRedisReady: () => false,
    };
  }

  const config = getRedisConfig();

  try {
    const opts = {
      retryStrategy(times: number) {
        if (times > 3) {
          console.warn('[Redis] Connection attempts exceeded limit, falling back to memory.');
          return null;
        }
        return Math.min(times * 200, 2000);
      },
      maxRetriesPerRequest: null,
      connectTimeout: 3000,
      lazyConnect: true,
      enableOfflineQueue: false,
    };

    pubClient = config.url ? new Redis(config.url, opts) : new Redis({ ...config, ...opts });
    subClient = config.url ? new Redis(config.url, opts) : new Redis({ ...config, ...opts });

    pubClient.on('error', (err) => {
      redisAvailable = false;
      console.warn('[Redis pub Error]:', err.message);
    });

    subClient.on('error', (err) => {
      redisAvailable = false;
      console.warn('[Redis sub Error]:', err.message);
    });

    pubClient.connect()
      .then(() => {
        redisAvailable = true;
        console.log('[Redis] Primary client connected successfully.');
      })
      .catch((err) => {
        redisAvailable = false;
        console.warn('[Redis] Failed to connect to Redis, using in-memory store:', err.message);
      });

    subClient.connect().catch(() => {});

  } catch (err: any) {
    redisAvailable = false;
    console.warn('[Redis Init Failed]:', err?.message);
  }

  return {
    pub: pubClient,
    sub: subClient,
    isRedisReady: () => redisAvailable,
  };
}

export async function redisGet(key: string): Promise<string | null> {
  if (redisAvailable && pubClient) {
    try {
      return await pubClient.get(key);
    } catch {
      // Fallback to memory
    }
  }

  const item = memoryStore.get(key);
  if (!item) return null;
  if (item.expiry && Date.now() > item.expiry) {
    memoryStore.delete(key);
    return null;
  }
  return item.value;
}

export async function redisSet(key: string, value: string, ttlSeconds?: number): Promise<void> {
  if (redisAvailable && pubClient) {
    try {
      if (ttlSeconds) {
        await pubClient.set(key, value, 'EX', ttlSeconds);
      } else {
        await pubClient.set(key, value);
      }
      return;
    } catch {
      // Fallback
    }
  }

  const expiry = ttlSeconds ? Date.now() + ttlSeconds * 1000 : undefined;
  memoryStore.set(key, { value, expiry });
}

export async function redisDel(key: string): Promise<void> {
  if (redisAvailable && pubClient) {
    try {
      await pubClient.del(key);
    } catch {
      // Fallback
    }
  }
  memoryStore.delete(key);
}

export async function redisExists(key: string): Promise<boolean> {
  if (redisAvailable && pubClient) {
    try {
      const res = await pubClient.exists(key);
      return res === 1;
    } catch {
      // Fallback
    }
  }
  const item = memoryStore.get(key);
  if (!item) return false;
  if (item.expiry && Date.now() > item.expiry) {
    memoryStore.delete(key);
    return false;
  }
  return true;
}
