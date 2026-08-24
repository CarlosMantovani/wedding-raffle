import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useRef, useState } from 'react';

import { transactionService } from '../../services/transactionService';
import type { TransactionStatusResponse } from '../../types/transaction';

export const PAYMENT_POLLING_DURATION_MS = 5 * 60 * 1000;
export const PAYMENT_POLLING_FIRST_INTERVAL_MS = 5 * 1000;
export const PAYMENT_POLLING_SECOND_INTERVAL_MS = 15 * 1000;
export const PAYMENT_POLLING_THIRD_INTERVAL_MS = 30 * 1000;

const FIRST_POLLING_PHASE_END_MS = 60 * 1000;
const SECOND_POLLING_PHASE_END_MS = 3 * 60 * 1000;
const POLLING_STORAGE_PREFIX = 'payment-polling-started-at:';

export function getPaymentPollingInterval(elapsedMs: number): number | false {
  if (elapsedMs >= PAYMENT_POLLING_DURATION_MS) {
    return false;
  }
  if (elapsedMs < FIRST_POLLING_PHASE_END_MS) {
    return PAYMENT_POLLING_FIRST_INTERVAL_MS;
  }
  if (elapsedMs < SECOND_POLLING_PHASE_END_MS) {
    return PAYMENT_POLLING_SECOND_INTERVAL_MS;
  }
  return PAYMENT_POLLING_THIRD_INTERVAL_MS;
}

export function getPaymentPollingStorageKey(externalReference: string) {
  return `${POLLING_STORAGE_PREFIX}${externalReference}`;
}

function getOrCreatePollingStartedAt(externalReference: string, now: number) {
  if (!externalReference) {
    return now;
  }

  const storageKey = getPaymentPollingStorageKey(externalReference);
  try {
    const storedValue = window.sessionStorage.getItem(storageKey);
    const storedStartedAt = storedValue === null ? Number.NaN : Number(storedValue);
    if (Number.isFinite(storedStartedAt) && storedStartedAt > 0 && storedStartedAt <= now) {
      return storedStartedAt;
    }
    window.sessionStorage.setItem(storageKey, String(now));
  } catch {
    return now;
  }
  return now;
}

export function usePaymentStatusPolling(externalReference: string, paymentId = '') {
  const queryClient = useQueryClient();
  const queryKey = useMemo(
    () => ['transaction-status', externalReference, paymentId] as const,
    [externalReference, paymentId],
  );
  const [pollingStartedAt] = useState(() =>
    getOrCreatePollingStartedAt(externalReference, Date.now()),
  );
  const pollingDeadline = pollingStartedAt + PAYMENT_POLLING_DURATION_MS;
  const [pollingExpired, setPollingExpired] = useState(() => Date.now() >= pollingDeadline);
  const [isOnline, setIsOnline] = useState(() => navigator.onLine);
  const [isVisible, setIsVisible] = useState(() => document.visibilityState === 'visible');
  const manualCheckRequested = useRef(false);

  const statusQuery = useQuery<TransactionStatusResponse>({
    enabled: (query) =>
      Boolean(externalReference) &&
      !pollingExpired &&
      isOnline &&
      isVisible &&
      (!query.state.data || query.state.data.status === 'PENDENTE'),
    queryKey,
    queryFn: ({ signal }) => {
      const manualCheck = manualCheckRequested.current;
      manualCheckRequested.current = false;
      if (!manualCheck && Date.now() >= pollingDeadline) {
        const cachedStatus = queryClient.getQueryData<TransactionStatusResponse>(queryKey);
        if (cachedStatus) {
          return cachedStatus;
        }
        return Promise.reject(new Error('Payment polling window expired.'));
      }
      return transactionService.getStatus(externalReference, paymentId, signal);
    },
    networkMode: 'online',
    refetchInterval: (query) => {
      if (
        pollingExpired ||
        !isOnline ||
        !isVisible ||
        (query.state.data && query.state.data.status !== 'PENDENTE')
      ) {
        return false;
      }
      return getPaymentPollingInterval(Date.now() - pollingStartedAt);
    },
    refetchIntervalInBackground: false,
    refetchOnReconnect: false,
    refetchOnWindowFocus: false,
    retry: false,
  });
  const terminalStatusReached = Boolean(statusQuery.data && statusQuery.data.status !== 'PENDENTE');

  useEffect(() => {
    if (pollingExpired || terminalStatusReached || !externalReference) {
      return;
    }

    const remainingMs = pollingDeadline - Date.now();
    const deadlineTimer = window.setTimeout(
      () => {
        setPollingExpired(true);
        void queryClient.cancelQueries({ exact: true, queryKey });
      },
      Math.max(0, remainingMs),
    );

    return () => window.clearTimeout(deadlineTimer);
  }, [
    externalReference,
    pollingDeadline,
    pollingExpired,
    queryClient,
    queryKey,
    terminalStatusReached,
  ]);

  useEffect(() => {
    const refreshExpiry = () => {
      if (Date.now() >= pollingDeadline) {
        setPollingExpired(true);
      }
    };
    const pauseCurrentRequest = () => {
      void queryClient.cancelQueries({ exact: true, queryKey });
    };
    const handleOnline = () => {
      refreshExpiry();
      setIsOnline(true);
    };
    const handleOffline = () => {
      setIsOnline(false);
      pauseCurrentRequest();
    };
    const handleVisibilityChange = () => {
      refreshExpiry();
      const visible = document.visibilityState === 'visible';
      setIsVisible(visible);
      if (!visible) {
        pauseCurrentRequest();
      }
    };

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      pauseCurrentRequest();
    };
  }, [pollingDeadline, queryClient, queryKey]);

  const checkStatusNow = () => {
    if (!isOnline || !isVisible) {
      return;
    }
    manualCheckRequested.current = true;
    void statusQuery.refetch({ cancelRefetch: true });
  };

  return {
    checkStatusNow,
    isOnline,
    isVisible,
    pollingExpired,
    statusQuery,
  };
}
