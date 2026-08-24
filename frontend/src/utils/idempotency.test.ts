import { clearIdempotencyKey, getOrCreateIdempotencyKey } from './idempotency';

describe('purchase idempotency keys', () => {
  beforeEach(() => {
    window.sessionStorage.clear();
  });

  it('reuses the key for the same logical action after a refresh', () => {
    const payload = { name: 'Guest User', phone: '11999999999', quantity: 2 };

    const firstKey = getOrCreateIdempotencyKey('checkout', payload);
    const retryKey = getOrCreateIdempotencyKey('checkout', payload);

    expect(retryKey).toBe(firstKey);
  });

  it('creates a new key when the payload changes or the prior action completes', () => {
    const firstKey = getOrCreateIdempotencyKey('checkout', { quantity: 1 });
    const changedPayloadKey = getOrCreateIdempotencyKey('checkout', { quantity: 2 });
    clearIdempotencyKey('checkout', changedPayloadKey);
    const nextActionKey = getOrCreateIdempotencyKey('checkout', { quantity: 2 });

    expect(changedPayloadKey).not.toBe(firstKey);
    expect(nextActionKey).not.toBe(changedPayloadKey);
  });
});
