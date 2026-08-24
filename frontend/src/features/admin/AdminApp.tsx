import { Suspense, useEffect, useState } from 'react';

import { AuthProvider, useAuth } from './AuthContext';
import { AdminCashPaymentPage } from './AdminCashPaymentPage';
import { AdminDashboardPage } from './AdminDashboardPage';
import { AdminDrawPage } from './AdminDrawPage';
import { AdminMessagesPage } from './AdminMessagesPage';
import { AdminSettingsPage } from './AdminSettingsPage';
import { AdminLoginPage } from './AdminLoginPage';

export default function AdminApp() {
  return (
    <AuthProvider>
      <AdminRoutes />
    </AuthProvider>
  );
}

function AdminRoutes() {
  const [path, setPath] = useState(window.location.pathname);
  const { isAuthenticated } = useAuth();

  useEffect(() => {
    const handleNavigation = () => setPath(window.location.pathname);
    window.addEventListener('popstate', handleNavigation);
    return () => window.removeEventListener('popstate', handleNavigation);
  }, []);

  if (path === '/admin/login') {
    if (isAuthenticated) {
      window.history.replaceState({}, '', '/admin');
      return <AdminDashboardPage />;
    }

    return <AdminLoginPage />;
  }

  if (!isAuthenticated) {
    window.history.replaceState({}, '', '/admin/login');
    return <AdminLoginPage />;
  }

  if (path === '/admin/draw') {
    return <AdminDrawPage />;
  }

  if (path === '/admin/cash-payment') {
    return <AdminCashPaymentPage />;
  }

  if (path === '/admin/settings') {
    return <AdminSettingsPage />;
  }

  if (path === '/admin/messages') {
    return <AdminMessagesPage />;
  }

  return (
    <Suspense fallback={<AdminLoading />}>
      <AdminDashboardPage />
    </Suspense>
  );
}

function AdminLoading() {
  return (
    <main className="grid min-h-screen place-items-center bg-cream px-6 text-charcoal">
      <p className="text-sm font-semibold text-warm-gray">Carregando área administrativa...</p>
    </main>
  );
}
