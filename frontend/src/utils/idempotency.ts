interface StoredIdempotencyKey {
  fingerprint: string;
  key: string;
}

const STORAGE_PREFIX = 'purchase-idempotency:';

export function getOrCreateIdempotencyKey(action: string, payload: object): string {
  const storageKey = `${STORAGE_PREFIX}${action}`;
  const fingerprint = JSON.stringify(payload);
  const stored = readStoredKey(storageKey);
  if (stored?.fingerprint === fingerprint) {
    return stored.key;
  }

  const key = globalThis.crypto.randomUUID();
  window.sessionStorage.setItem(
    storageKey,
    JSON.stringify({ fingerprint, key } satisfies StoredIdempotencyKey),
  );
  return key;
}

export function clearIdempotencyKey(action: string, key: string): void {
  const storageKey = `${STORAGE_PREFIX}${action}`;
  const stored = readStoredKey(storageKey);
  if (stored?.key === key) {
    window.sessionStorage.removeItem(storageKey);
  }
}

function readStoredKey(storageKey: string): StoredIdempotencyKey | null {
  const serialized = window.sessionStorage.getItem(storageKey);
  if (!serialized) return null;

  try {
    const parsed = JSON.parse(serialized) as Partial<StoredIdempotencyKey>;
    return typeof parsed.fingerprint === 'string' && typeof parsed.key === 'string'
      ? { fingerprint: parsed.fingerprint, key: parsed.key }
      : null;
  } catch {
    return null;
  }
}
