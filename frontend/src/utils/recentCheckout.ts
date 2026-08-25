import type { TransactionCreateResponse } from '../types/transaction';

export interface RecentCheckout {
  checkoutUrl: string;
  createdAt: number;
  externalReference: string;
}

const RECENT_CHECKOUT_STORAGE_KEY = 'recent-mercado-pago-checkout';
const RECENT_CHECKOUT_TTL_MS = 30 * 60 * 1000;

export function saveRecentCheckout(response: TransactionCreateResponse): void {
  window.sessionStorage.setItem(
    RECENT_CHECKOUT_STORAGE_KEY,
    JSON.stringify({
      checkoutUrl: response.checkoutUrl,
      createdAt: Date.now(),
      externalReference: response.externalReference,
    } satisfies RecentCheckout),
  );
}

export function readRecentCheckout(): RecentCheckout | null {
  const serialized = window.sessionStorage.getItem(RECENT_CHECKOUT_STORAGE_KEY);
  if (!serialized) return null;

  try {
    const parsed = JSON.parse(serialized) as Partial<RecentCheckout>;
    if (
      typeof parsed.checkoutUrl !== 'string' ||
      typeof parsed.createdAt !== 'number' ||
      typeof parsed.externalReference !== 'string'
    ) {
      clearRecentCheckout();
      return null;
    }

    if (Date.now() - parsed.createdAt > RECENT_CHECKOUT_TTL_MS) {
      clearRecentCheckout();
      return null;
    }

    return {
      checkoutUrl: parsed.checkoutUrl,
      createdAt: parsed.createdAt,
      externalReference: parsed.externalReference,
    };
  } catch {
    clearRecentCheckout();
    return null;
  }
}

export function clearRecentCheckout(externalReference?: string): void {
  if (!externalReference) {
    window.sessionStorage.removeItem(RECENT_CHECKOUT_STORAGE_KEY);
    return;
  }

  const current = readRecentCheckout();
  if (current?.externalReference === externalReference) {
    window.sessionStorage.removeItem(RECENT_CHECKOUT_STORAGE_KEY);
  }
}
