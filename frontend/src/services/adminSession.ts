import type { AdminSession, AuthLoginResponse } from '../types/auth';

const ADMIN_SESSION_KEY = 'wedding-raffle-admin-session';

export function createAdminSession(response: AuthLoginResponse): AdminSession {
  return {
    tokenType: response.tokenType,
    accessToken: response.accessToken,
    expiresAt: Date.now() + response.expiresIn * 1000,
    roles: response.roles ?? ['MASTER'],
  };
}

export function getStoredAdminSession(): AdminSession | null {
  const rawSession = window.sessionStorage.getItem(ADMIN_SESSION_KEY);
  if (!rawSession) return null;

  try {
    const session = JSON.parse(rawSession) as AdminSession;
    if (!session.accessToken || session.expiresAt <= Date.now()) {
      clearStoredAdminSession();
      return null;
    }

    return { ...session, roles: session.roles ?? ['MASTER'] };
  } catch {
    clearStoredAdminSession();
    return null;
  }
}

export function storeAdminSession(session: AdminSession) {
  window.sessionStorage.setItem(ADMIN_SESSION_KEY, JSON.stringify(session));
}

export function clearStoredAdminSession() {
  window.sessionStorage.removeItem(ADMIN_SESSION_KEY);
}
