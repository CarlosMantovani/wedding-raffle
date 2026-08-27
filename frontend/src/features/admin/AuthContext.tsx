/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';

import { clearStoredAdminSession, getStoredAdminSession } from '../../services/adminSession';
import type { AdminSession } from '../../types/auth';

interface AuthContextValue {
  session: AdminSession | null;
  isAuthenticated: boolean;
  isMaster: boolean;
  refreshSession: () => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AdminSession | null>(() => getStoredAdminSession());

  const value = useMemo<AuthContextValue>(
    () => ({
      session,
      isAuthenticated: Boolean(session),
      isMaster: session?.roles.includes('MASTER') ?? false,
      refreshSession: () => setSession(getStoredAdminSession()),
      logout: () => {
        clearStoredAdminSession();
        setSession(null);
        window.history.replaceState({}, '', '/admin/login');
        window.dispatchEvent(new PopStateEvent('popstate'));
      },
    }),
    [session],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider.');
  }

  return context;
}
