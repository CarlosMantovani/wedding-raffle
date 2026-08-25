import { lazy, Suspense } from 'react';

import { BuyNumbersPage } from './features/buy-numbers/BuyNumbersPage';

const AdminApp = lazy(() => import('./features/admin/AdminApp'));
const FlagRankingPage = lazy(() =>
  import('./features/flag-ranking/FlagRankingPage').then((module) => ({
    default: module.FlagRankingPage,
  })),
);
const PaymentReturnPage = lazy(() =>
  import('./features/payment-return/PaymentReturnPage').then((module) => ({
    default: module.PaymentReturnPage,
  })),
);
const RecoveryNumbersPage = lazy(() =>
  import('./features/buy-numbers/RecoveryNumbersPage').then((module) => ({
    default: module.RecoveryNumbersPage,
  })),
);

export function App() {
  const path = window.location.pathname;

  if (path.startsWith('/admin')) {
    return (
      <Suspense
        fallback={
          <main className="grid min-h-screen place-items-center bg-cream px-6 text-charcoal">
            <p className="text-sm font-semibold text-warm-gray">Carregando área administrativa...</p>
          </main>
        }
      >
        <AdminApp />
      </Suspense>
    );
  }

  if (path.startsWith('/payment-return/')) {
    return (
      <Suspense fallback={<PublicPageLoading />}>
        <PaymentReturnPage />
      </Suspense>
    );
  }

  if (path === '/flag-ranking') {
    return (
      <Suspense fallback={<PublicPageLoading />}>
        <FlagRankingPage />
      </Suspense>
    );
  }

  if (path === '/buy') {
    return <BuyNumbersPage showBackLink />;
  }

  if (path === '/recover') {
    return (
      <Suspense fallback={<PublicPageLoading />}>
        <RecoveryNumbersPage />
      </Suspense>
    );
  }

  return <BuyNumbersPage />;
}

function PublicPageLoading() {
  return (
    <main className="grid min-h-screen place-items-center bg-cream px-6 text-charcoal">
      <p className="text-sm font-semibold text-warm-gray">Carregando...</p>
    </main>
  );
}
