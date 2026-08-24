import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';

import { transactionService } from '../../services/transactionService';
import type { TransactionStatusResponse } from '../../types/transaction';
import { PaymentReturnPage } from './PaymentReturnPage';
import {
  PAYMENT_POLLING_DURATION_MS,
  getPaymentPollingStorageKey,
} from './usePaymentStatusPolling';

vi.mock('../../services/transactionService', () => ({
  transactionService: {
    create: vi.fn(),
    getLuckyNumbersPdfUrl: vi.fn(),
    getStatus: vi.fn(),
  },
}));

const mockedTransactionService = vi.mocked(transactionService);
const externalReference = 'external-reference-polling';
const pendingTransaction: TransactionStatusResponse = {
  externalReference,
  recoveryCode: '4821',
  luckyNumbers: [],
  participantFlagEmoji: '🇧🇷',
  participantFlagName: 'Brasil',
  quantity: 2,
  status: 'PENDENTE',
  totalAmount: '20.00',
};
const approvedTransaction: TransactionStatusResponse = {
  ...pendingTransaction,
  luckyNumbers: ['00042', '12345'],
  status: 'APROVADO',
};

let online = true;
let visibilityState: DocumentVisibilityState = 'visible';

function TestQueryProvider({ children }: { children: ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { gcTime: Infinity, retry: false },
    },
  });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

function renderPage() {
  window.history.pushState(
    {},
    '',
    `/payment-return/pending?external_reference=${externalReference}`,
  );
  return render(<PaymentReturnPage />, { wrapper: TestQueryProvider });
}

async function flushAsyncWork() {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(0);
  });
}

async function advanceBy(milliseconds: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(milliseconds);
  });
}

async function setOnline(value: boolean) {
  await act(async () => {
    online = value;
    window.dispatchEvent(new Event(value ? 'online' : 'offline'));
    await vi.advanceTimersByTimeAsync(0);
  });
}

async function setVisibility(value: DocumentVisibilityState) {
  await act(async () => {
    visibilityState = value;
    document.dispatchEvent(new Event('visibilitychange'));
    await vi.advanceTimersByTimeAsync(0);
  });
}

describe('PaymentReturnPage polling', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-24T15:00:00Z'));
    vi.clearAllMocks();
    window.sessionStorage.clear();
    online = true;
    visibilityState = 'visible';
    Object.defineProperty(navigator, 'onLine', {
      configurable: true,
      get: () => online,
    });
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      get: () => visibilityState,
    });
    mockedTransactionService.getStatus.mockResolvedValue(pendingTransaction);
    mockedTransactionService.getLuckyNumbersPdfUrl.mockReturnValue('/numbers.pdf');
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('polls every five seconds during the first minute', async () => {
    renderPage();
    await flushAsyncWork();
    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(1);

    await advanceBy(4_999);
    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(1);
    await advanceBy(1);
    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(2);
    await advanceBy(50_000);
    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(12);
  });

  it('transitions to fifteen-second polling after the first minute', async () => {
    renderPage();
    await flushAsyncWork();
    await advanceBy(60_000);
    const callsAtOneMinute = mockedTransactionService.getStatus.mock.calls.length;

    await advanceBy(14_999);
    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(callsAtOneMinute);
    await advanceBy(1);
    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(callsAtOneMinute + 1);
  });

  it('transitions to thirty-second polling after the third minute', async () => {
    renderPage();
    await flushAsyncWork();
    await advanceBy(180_000);
    const callsAtThreeMinutes = mockedTransactionService.getStatus.mock.calls.length;

    await advanceBy(29_999);
    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(callsAtThreeMinutes);
    await advanceBy(1);
    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(callsAtThreeMinutes + 1);
  });

  it('stops automatically after five minutes and only allows a controlled manual check', async () => {
    renderPage();
    await flushAsyncWork();
    await advanceBy(PAYMENT_POLLING_DURATION_MS - 1);
    const callsBeforeDeadline = mockedTransactionService.getStatus.mock.calls.length;
    await advanceBy(1);
    const callsAtDeadline = mockedTransactionService.getStatus.mock.calls.length;

    expect(callsAtDeadline).toBe(callsBeforeDeadline);
    expect(
      screen.getByText(/consulta automática foi encerrada após cinco minutos/i),
    ).toBeInTheDocument();
    await advanceBy(PAYMENT_POLLING_DURATION_MS);
    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(callsAtDeadline);

    fireEvent.click(screen.getByRole('button', { name: 'Consultar status agora' }));
    await flushAsyncWork();
    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(callsAtDeadline + 1);
    await advanceBy(PAYMENT_POLLING_DURATION_MS);
    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(callsAtDeadline + 1);
  });

  it('stops immediately when the payment becomes approved', async () => {
    mockedTransactionService.getStatus
      .mockResolvedValueOnce(pendingTransaction)
      .mockResolvedValueOnce(approvedTransaction);
    renderPage();
    await flushAsyncWork();
    await advanceBy(5_000);
    await advanceBy(1);

    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(2);
    expect(screen.getByText('00042')).toBeInTheDocument();
    await advanceBy(PAYMENT_POLLING_DURATION_MS);
    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(2);
  });

  it('stops immediately in another existing terminal state', async () => {
    mockedTransactionService.getStatus.mockResolvedValue({
      ...pendingTransaction,
      status: 'CANCELADO',
    });
    renderPage();
    await flushAsyncWork();

    expect(screen.getByText('Pagamento cancelado')).toBeInTheDocument();
    await advanceBy(PAYMENT_POLLING_DURATION_MS);
    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(1);
  });

  it('pauses polling while the browser is offline', async () => {
    renderPage();
    await flushAsyncWork();
    await setOnline(false);
    const callsBeforeOfflineWait = mockedTransactionService.getStatus.mock.calls.length;

    await advanceBy(30_000);
    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(callsBeforeOfflineWait);
    expect(screen.getByText(/consulta foi pausada/i)).toBeInTheDocument();
    expect(screen.queryByText('Pagamento recusado')).not.toBeInTheDocument();
  });

  it('performs one controlled check when the connection returns', async () => {
    renderPage();
    await flushAsyncWork();
    await setOnline(false);
    await advanceBy(20_000);
    const callsBeforeReconnect = mockedTransactionService.getStatus.mock.calls.length;

    await setOnline(true);
    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(callsBeforeReconnect + 1);
  });

  it('pauses polling while the document is hidden', async () => {
    renderPage();
    await flushAsyncWork();
    await setVisibility('hidden');
    const callsBeforeHiddenWait = mockedTransactionService.getStatus.mock.calls.length;

    await advanceBy(30_000);
    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(callsBeforeHiddenWait);
  });

  it('performs one controlled check when the document becomes visible', async () => {
    renderPage();
    await flushAsyncWork();
    await setVisibility('hidden');
    await advanceBy(20_000);
    const callsBeforeVisibilityReturn = mockedTransactionService.getStatus.mock.calls.length;

    await setVisibility('visible');
    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(
      callsBeforeVisibilityReturn + 1,
    );
  });

  it('cancels polling timers and requests on unmount', async () => {
    const view = renderPage();
    await flushAsyncWork();
    view.unmount();
    const callsAtUnmount = mockedTransactionService.getStatus.mock.calls.length;

    await advanceBy(PAYMENT_POLLING_DURATION_MS);
    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(callsAtUnmount);
  });

  it('does not create duplicate timers after repeated visibility changes', async () => {
    renderPage();
    await flushAsyncWork();
    for (let index = 0; index < 3; index += 1) {
      await setVisibility('hidden');
      await setVisibility('visible');
    }
    const callsAfterVisibilityChanges = mockedTransactionService.getStatus.mock.calls.length;

    await advanceBy(4_999);
    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(callsAfterVisibilityChanges);
    await advanceBy(1);
    expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(
      callsAfterVisibilityChanges + 1,
    );
  });

  it('does not present a timeout as a rejected payment', async () => {
    mockedTransactionService.getStatus.mockRejectedValue({
      code: 'PAYMENT_PROVIDER_ERROR',
      message: 'Unable to communicate with the payment provider.',
      status: 502,
    });
    renderPage();
    await flushAsyncWork();

    expect(
      screen.getByText('Não foi possível confirmar o status neste momento'),
    ).toBeInTheDocument();
    expect(screen.getByText(/pagamento pode continuar sendo processado/i)).toBeInTheDocument();
    expect(screen.queryByText('Pagamento recusado')).not.toBeInTheDocument();
  });

  it('preserves the polling window and transaction reference after refresh without creating a purchase', async () => {
    const firstView = renderPage();
    await flushAsyncWork();
    const storageKey = getPaymentPollingStorageKey(externalReference);
    const firstStartedAt = window.sessionStorage.getItem(storageKey);
    await advanceBy(120_000);
    firstView.unmount();

    renderPage();
    await flushAsyncWork();

    expect(window.sessionStorage.getItem(storageKey)).toBe(firstStartedAt);
    expect(mockedTransactionService.create).not.toHaveBeenCalled();
    expect(mockedTransactionService.getStatus).toHaveBeenLastCalledWith(
      externalReference,
      '',
      expect.any(AbortSignal),
    );
  });

  it('sends the Mercado Pago payment id when it is present in the return URL', async () => {
    window.history.pushState(
      {},
      '',
      `/payment-return/pending?external_reference=${externalReference}&payment_id=123`,
    );
    render(<PaymentReturnPage />, { wrapper: TestQueryProvider });
    await flushAsyncWork();

    expect(mockedTransactionService.getStatus).toHaveBeenCalledWith(
      externalReference,
      '123',
      expect.any(AbortSignal),
    );
  });

  it('does not allow an old cancelled request to overwrite a newer response', async () => {
    let resolveOldRequest: ((value: TransactionStatusResponse) => void) | undefined;
    mockedTransactionService.getStatus
      .mockImplementationOnce(
        () =>
          new Promise<TransactionStatusResponse>((resolve) => {
            resolveOldRequest = resolve;
          }),
      )
      .mockResolvedValueOnce(approvedTransaction);
    renderPage();
    await flushAsyncWork();
    await setVisibility('hidden');
    await setVisibility('visible');
    await advanceBy(1);

    expect(screen.getByText('00042')).toBeInTheDocument();
    await act(async () => {
      resolveOldRequest?.(pendingTransaction);
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(screen.getByText('00042')).toBeInTheDocument();
    expect(screen.queryByText('Pagamento pendente')).not.toBeInTheDocument();
  });

  it.each([300, 1_000, 3_000])(
    'does not overlap polling requests with %i ms network latency',
    async (latencyMs) => {
      mockedTransactionService.getStatus.mockImplementation(
        () =>
          new Promise<TransactionStatusResponse>((resolve) => {
            window.setTimeout(() => resolve(pendingTransaction), latencyMs);
          }),
      );
      renderPage();
      await flushAsyncWork();
      expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(1);

      await advanceBy(latencyMs + 4_999);
      expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(1);
      await advanceBy(1);
      expect(mockedTransactionService.getStatus).toHaveBeenCalledTimes(2);
    },
  );
});
