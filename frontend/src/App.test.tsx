import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { App } from './App';
import { CountdownPanel } from './features/buy-numbers/CountdownPanel';
import { adminTransactionService } from './services/adminTransactionService';
import { createAdminSession, storeAdminSession } from './services/adminSession';
import { authService } from './services/authService';
import { homeService } from './services/homeService';
import { raffleConfigService } from './services/raffleConfigService';
import { raffleService } from './services/raffleService';
import { transactionService } from './services/transactionService';

vi.mock('./services/transactionService', () => ({
  transactionService: {
    create: vi.fn(),
    getLuckyNumbersPdfUrl: vi.fn(),
    getStatus: vi.fn(),
    quote: vi.fn(),
    recover: vi.fn(),
  },
}));

vi.mock('./services/homeService', () => ({
  homeService: {
    getFlagRanking: vi.fn(),
    getSummary: vi.fn(),
  },
}));

vi.mock('./services/authService', () => ({
  authService: {
    login: vi.fn(),
  },
}));

vi.mock('./services/adminTransactionService', () => ({
  adminTransactionService: {
    createCashTransaction: vi.fn(),
    deleteCashTransaction: vi.fn(),
    getParticipantLuckyNumbersPdf: vi.fn(),
    getSummary: vi.fn(),
    list: vi.fn(),
    listGiftMessages: vi.fn(),
    resolveCapacityReview: vi.fn(),
  },
}));

vi.mock('./services/raffleService', () => ({
  raffleService: {
    draw: vi.fn(),
    getEligibleNumbers: vi.fn(),
    getResult: vi.fn(),
  },
}));

vi.mock('./services/raffleConfigService', () => ({
  raffleConfigService: {
    getConfig: vi.fn(),
    updateCombo: vi.fn(),
    updateScheduledDrawAt: vi.fn(),
    updateUnitPrice: vi.fn(),
  },
}));

const mockedTransactionService = vi.mocked(transactionService);
const mockedHomeService = vi.mocked(homeService);
const mockedAuthService = vi.mocked(authService);
const mockedAdminTransactionService = vi.mocked(adminTransactionService);
const mockedRaffleService = vi.mocked(raffleService);
const mockedRaffleConfigService = vi.mocked(raffleConfigService);
const originalLocation = window.location;

function renderApp(path = '/') {
  window.history.pushState({}, '', path);

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>,
  );
}

describe('App', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: originalLocation,
    });
    window.history.pushState({}, '', '/');
    window.sessionStorage.clear();
    mockedTransactionService.quote.mockResolvedValue({
      name: 'Guest User',
      phone: '11999999999',
      quantity: 1,
      unitPrice: '10.00',
      totalAmount: '10.00',
      comboId: null,
      availableCombos: [],
    });
    mockedTransactionService.getLuckyNumbersPdfUrl.mockReturnValue(
      'http://localhost:8080/transactions/external-reference/lucky-numbers.pdf',
    );
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
    });
    mockedHomeService.getSummary.mockResolvedValue({
      scheduledDrawAt: null,
      raffleResult: null,
      flagRanking: [
        {
          code: 'BRAZIL',
          emoji: '🇧🇷',
          name: 'Brasil',
          position: 1,
          progressPercent: 100.0,
        },
      ],
    });
    mockedHomeService.getFlagRanking.mockResolvedValue([
      {
        code: 'BRAZIL',
        emoji: '🇧🇷',
        name: 'Brasil',
        position: 1,
        progressPercent: 100.0,
      },
    ]);
    mockedAdminTransactionService.list.mockResolvedValue({
      content: [
        {
          createdAt: '2026-08-14T18:00:00-03:00',
          email: 'guest@example.com',
          externalReference: 'external-reference',
          luckyNumbers: ['00001', '00002'],
          name: 'Guest User',
          paymentMethod: 'MERCADO_PAGO',
          phone: '11999999999',
          quantity: 2,
          status: 'APROVADO',
          totalAmount: '20.00',
        },
      ],
      first: true,
      last: true,
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    mockedAdminTransactionService.listGiftMessages.mockResolvedValue({
      content: [
        {
          createdAt: '2026-08-14T18:00:00-03:00',
          externalReference: 'external-reference',
          giftMessage: 'Felicidades ao casal!',
          name: 'Guest User',
        },
      ],
      first: true,
      last: true,
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    mockedAdminTransactionService.getSummary.mockResolvedValue({
      approvedLuckyNumbers: 2,
      approvedRevenue: '20.00',
      totalTransactions: 1,
    });
    mockedAdminTransactionService.getParticipantLuckyNumbersPdf.mockResolvedValue(
      new Blob(['%PDF']),
    );
    mockedRaffleConfigService.getConfig.mockResolvedValue({
      scheduledDrawAt: null,
      unitPrice: '10.00',
      updatedAt: '2026-08-14T18:00:00-03:00',
      combos: [],
    });
    mockedRaffleService.getEligibleNumbers.mockResolvedValue([
      {
        luckyNumber: '00001',
        participantFlagEmoji: 'ðŸ‡§ðŸ‡·',
        participantFlagName: 'Brasil',
      },
      {
        luckyNumber: '00042',
        participantFlagEmoji: 'ðŸ‡¨ðŸ‡¦',
        participantFlagName: 'Canada',
      },
    ]);
  });

  it('renders the flag ranking preview on the purchase page', async () => {
    renderApp();

    expect(await screen.findByText('Ranking de bandeiras')).toBeInTheDocument();
    expect(screen.getByText('Uma bandeira exclusiva por telefone.')).toBeInTheDocument();
    expect(screen.getByText('Novas compras somam pontos na mesma bandeira.')).toBeInTheDocument();
    expect(
      screen.getByText('Em caso de empate de bandeiras, a compra mais recente fica na frente'),
    ).toBeInTheDocument();
    expect(screen.getByText('A líder também ganhará um prêmio especial.')).toBeInTheDocument();
    expect(await screen.findAllByText('Brasil')).toHaveLength(2);
    expect(screen.getAllByRole('progressbar', { name: 'Progresso relativo de Brasil' })).toHaveLength(2);
    expect(screen.getByRole('link', { name: 'Ver top 30' })).toHaveAttribute('href', '/flag-ranking');
  });

  it('renders the top thirty flag ranking page', async () => {
    mockedHomeService.getSummary.mockResolvedValue({
      scheduledDrawAt: '2026-09-06T02:00:00Z',
      raffleResult: null,
      flagRanking: [],
    });
    mockedHomeService.getFlagRanking.mockResolvedValue(
      Array.from({ length: 30 }, (_, index) => ({
        code: `FLAG_${index + 1}`,
        emoji: '🇧🇷',
        name: `Bandeira ${index + 1}`,
        position: index + 1,
        progressPercent: Number((((30 - index) * 100) / 30).toFixed(2)),
      })),
    );

    renderApp('/flag-ranking');

    expect(await screen.findByText('Top 30 bandeiras')).toBeInTheDocument();
    expect(await screen.findByText('Contagem para o sorteio')).toBeInTheDocument();
    expect(screen.getByText('Atualiza a cada 5 minutos')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Voltar' })).toHaveAttribute('href', '/');
    expect((await screen.findAllByText('Bandeira 30')).length).toBeGreaterThan(0);
    expect(mockedHomeService.getFlagRanking).toHaveBeenCalledTimes(1);
  });

  it('refreshes the top thirty flag ranking every five minutes', async () => {
    vi.useFakeTimers();
    mockedHomeService.getFlagRanking.mockResolvedValue([
      {
        code: 'BRAZIL',
        emoji: '🇧🇷',
        name: 'Brasil',
        position: 1,
        progressPercent: 100.0,
      },
    ]);

    try {
      renderApp('/flag-ranking');

      await Promise.resolve();

      expect(mockedHomeService.getFlagRanking).toHaveBeenCalledTimes(1);

      await vi.advanceTimersByTimeAsync(5 * 60 * 1000);
      expect(mockedHomeService.getFlagRanking).toHaveBeenCalledTimes(2);
    } finally {
      vi.useRealTimers();
    }
  });

  it('keeps purchase open without rendering countdown before scheduled draw', async () => {
    mockedHomeService.getSummary.mockResolvedValue({
      scheduledDrawAt: '2026-09-06T02:00:00Z',
      raffleResult: null,
      flagRanking: [],
    });

    renderApp();

    expect(await screen.findByRole('button', { name: 'Continuar' })).toBeInTheDocument();
    expect(screen.queryByText('Contagem para o sorteio')).not.toBeInTheDocument();
  });

  it('shows the final urgency message when draw is less than five minutes away', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-06T01:56:00Z'));

    try {
      render(<CountdownPanel scheduledDrawAt="2026-09-06T02:00:00Z" />);

      expect(screen.getByText('Última chamada')).toBeInTheDocument();
      expect(screen.getByText('Últimos 5 minutos para garantir seus números.')).toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });

  it('shows draw closed and blocks public purchase when scheduled draw has passed', async () => {
    mockedHomeService.getSummary.mockResolvedValue({
      scheduledDrawAt: '2026-08-01T02:00:00Z',
      raffleResult: null,
      flagRanking: [],
    });

    renderApp('/buy');

    expect(await screen.findAllByText('Sorteio encerrado')).toHaveLength(1);
    expect(
      screen.getByText('Sorteio encerrado. Não é mais possível comprar números.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Continuar' })).not.toBeInTheDocument();
    expect(mockedTransactionService.quote).not.toHaveBeenCalled();
  });

  it('renders the raffle winner on the purchase page when result exists', async () => {
    mockedHomeService.getSummary.mockResolvedValue({
      scheduledDrawAt: '2026-08-01T02:00:00Z',
      raffleResult: {
        drawnAt: '2026-08-01T03:00:00Z',
        participantFlagEmoji: '🇧🇷',
        participantFlagName: 'Brasil',
        winnerName: 'Winner Guest',
        winningNumber: '00042',
      },
      flagRanking: [],
    });

    renderApp();

    expect(await screen.findByText('Número ganhador')).toBeInTheDocument();
    expect(screen.getByText('00042')).toBeInTheDocument();
    expect(screen.getByText('Winner Guest')).toBeInTheDocument();
    expect(screen.getByText('Brasil')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '🇧🇷' })).toBeInTheDocument();
  });

  it('does not render the raffle winner before the scheduled draw is closed', async () => {
    mockedHomeService.getSummary.mockResolvedValue({
      scheduledDrawAt: '2026-09-06T02:00:00Z',
      raffleResult: {
        drawnAt: '2026-08-01T03:00:00Z',
        participantFlagEmoji: '🇧🇷',
        participantFlagName: 'Brasil',
        winnerName: 'Winner Guest',
        winningNumber: '00042',
      },
      flagRanking: [],
    });

    renderApp();

    expect(await screen.findByRole('button', { name: 'Continuar' })).toBeInTheDocument();
    expect(screen.queryByText('Número ganhador')).not.toBeInTheDocument();
    expect(screen.queryByText('00042')).not.toBeInTheDocument();
  });

  it('requires name and phone before the quantity step', async () => {
    const user = userEvent.setup();
    renderApp('/buy');

    expect(screen.getByRole('button', { name: 'Continuar' })).toBeDisabled();

    await user.type(screen.getByLabelText('Nome'), 'Guest User');

    expect(screen.getByRole('button', { name: 'Continuar' })).toBeDisabled();
    expect(screen.queryByText('Quantos números você quer?')).not.toBeInTheDocument();

    await user.type(screen.getByLabelText('Telefone'), '44988549696');

    expect(screen.getByLabelText('Telefone')).toHaveValue('(44) 98854-9696');
    expect(screen.getByRole('button', { name: 'Continuar' })).toBeEnabled();
  });

  it('shows quote values when quantity changes', async () => {
    const user = userEvent.setup();
    mockedTransactionService.quote
      .mockResolvedValueOnce({
        name: 'Guest User',
        phone: '11999999999',
        quantity: 1,
        unitPrice: '10.00',
        totalAmount: '10.00',
        comboId: null,
        availableCombos: [],
      })
      .mockResolvedValueOnce({
        name: 'Guest User',
        phone: '11999999999',
        quantity: 2,
        unitPrice: '10.00',
        totalAmount: '20.00',
        comboId: null,
        availableCombos: [],
      });

    renderApp('/buy');

    await user.type(screen.getByLabelText('Nome'), 'Guest User');
    await user.type(screen.getByLabelText('Telefone'), '11999999999');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await screen.findAllByText('R$ 10,00');

    await user.click(screen.getByRole('button', { name: 'Aumentar quantidade' }));

    expect(await screen.findByText('R$ 20,00')).toBeInTheDocument();
  });

  it('accepts a manually typed quantity and requests the regular backend quote', async () => {
    const user = userEvent.setup();
    mockedTransactionService.quote.mockImplementation(async (request) => ({
      availableCombos: [],
      comboId: null,
      name: request.name,
      phone: request.phone,
      quantity: request.quantity,
      totalAmount: (request.quantity * 50).toFixed(2),
      unitPrice: '50.00',
    }));

    renderApp('/buy');
    await user.type(screen.getByLabelText('Nome'), 'Guest User');
    await user.type(screen.getByLabelText('Telefone'), '11999999999');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    const quantityInput = await screen.findByLabelText('Quantidade de números');
    await user.click(quantityInput);
    await user.keyboard('17');

    await waitFor(() =>
      expect(mockedTransactionService.quote).toHaveBeenLastCalledWith({
        name: 'Guest User',
        phone: '11999999999',
        quantity: 17,
      }),
    );
    expect(quantityInput).toHaveValue(17);
  });

  it('selects backend-priced combo, shows correct savings, and clears it after manual change', async () => {
    const user = userEvent.setup();
    const combos = [
      {
        active: true,
        averagePricePerNumber: '42.50',
        discountPercent: '15.00',
        displayOrder: 4,
        highlightBestValue: true,
        highlightMostChosen: false,
        id: 4,
        price: '1275.00',
        quantity: 30,
        regularPrice: '1500.00',
        savingsAmount: '225.00',
      },
    ];
    mockedTransactionService.quote.mockImplementation(async (request) => ({
      availableCombos: combos,
      comboId: request.comboId ?? null,
      name: request.name,
      phone: request.phone,
      quantity: request.quantity,
      totalAmount: request.comboId ? '1275.00' : (request.quantity * 50).toFixed(2),
      unitPrice: '50.00',
    }));
    mockedTransactionService.create.mockReturnValue(new Promise(() => undefined));

    renderApp('/buy');
    await user.type(screen.getByLabelText('Nome'), 'Guest User');
    await user.type(screen.getByLabelText('Telefone'), '11999999999');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));

    const comboButton = await screen.findByRole('button', { name: /30 números/i });
    expect(comboButton).toHaveTextContent('Melhor valor');
    await user.click(comboButton);

    await waitFor(() =>
      expect(mockedTransactionService.quote).toHaveBeenLastCalledWith({
        comboId: 4,
        name: 'Guest User',
        phone: '11999999999',
        quantity: 30,
      }),
    );
    expect(await screen.findAllByText('R$ 225,00')).not.toHaveLength(0);
    expect(screen.queryByText('R$ 975,00')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Pagar com Mercado Pago/i }));
    expect(mockedTransactionService.create).toHaveBeenCalledWith(
      {
        comboId: 4,
        name: 'Guest User',
        phone: '11999999999',
        quantity: 30,
      },
      expect.any(String),
    );

    await user.click(screen.getByRole('button', { name: 'Aumentar quantidade' }));
    await waitFor(() =>
      expect(mockedTransactionService.quote).toHaveBeenLastCalledWith({
        name: 'Guest User',
        phone: '11999999999',
        quantity: 31,
      }),
    );
  });

  it('creates transaction once and redirects to Mercado Pago checkout', async () => {
    const user = userEvent.setup();
    const assign = vi.fn();
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { ...window.location, pathname: '/buy', assign },
    });
    mockedTransactionService.create.mockResolvedValue({
      checkoutUrl: 'https://checkout.example.com',
      externalReference: 'external-reference',
      preferenceId: 'preference-id',
      recoveryCode: '4821',
    });

    renderApp('/buy');

    await user.type(screen.getByLabelText('Nome'), 'Guest User');
    await user.type(screen.getByLabelText('Telefone'), '(11) 99999-9999');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await screen.findAllByText('R$ 10,00');

    await user.click(screen.getByRole('button', { name: /Pagar com Mercado Pago/i }));
    await waitFor(() => expect(mockedTransactionService.create).toHaveBeenCalledTimes(1));

    expect(mockedTransactionService.create).toHaveBeenCalledWith(
      {
        name: 'Guest User',
        phone: '11999999999',
        quantity: 1,
      },
      expect.any(String),
    );
    expect(assign).toHaveBeenCalledWith('https://checkout.example.com');
  });

  it('sends optional gift message trimmed from the quantity step', async () => {
    const user = userEvent.setup();
    const assign = vi.fn();
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { ...window.location, pathname: '/buy', assign },
    });
    mockedTransactionService.create.mockResolvedValue({
      checkoutUrl: 'https://checkout.example.com',
      externalReference: 'external-reference',
      preferenceId: 'preference-id',
      recoveryCode: '4821',
    });

    renderApp('/buy');

    await user.type(screen.getByLabelText('Nome'), 'Guest User');
    await user.type(screen.getByLabelText('Telefone'), '(11) 99999-9999');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await screen.findAllByText('R$ 10,00');
    expect(screen.getByText('0/280')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Mensagem para o casal (opcional)'), '  Felicidades!  ');
    expect(screen.getByText('16/280')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Pagar com Mercado Pago/i }));

    await waitFor(() => expect(mockedTransactionService.create).toHaveBeenCalledTimes(1));
    expect(mockedTransactionService.create).toHaveBeenCalledWith(
      {
        giftMessage: 'Felicidades!',
        name: 'Guest User',
        phone: '11999999999',
        quantity: 1,
      },
      expect.any(String),
    );
  });

  it('reuses the checkout idempotency key after a failed request', async () => {
    const user = userEvent.setup();
    const assign = vi.fn();
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { ...window.location, pathname: '/buy', assign },
    });
    mockedTransactionService.create
      .mockRejectedValueOnce({ code: 'PAYMENT_PROVIDER_ERROR' })
      .mockResolvedValueOnce({
        checkoutUrl: 'https://checkout.example.com',
        externalReference: 'external-reference',
        preferenceId: 'preference-id',
        recoveryCode: '4821',
      });

    renderApp('/buy');
    await user.type(screen.getByLabelText('Nome'), 'Guest User');
    await user.type(screen.getByLabelText('Telefone'), '(11) 99999-9999');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await screen.findAllByText('R$ 10,00');

    await user.click(screen.getByRole('button', { name: /Pagar com Mercado Pago/i }));
    await screen.findByRole('alert');
    const firstKey = mockedTransactionService.create.mock.calls[0][1];
    await user.click(screen.getByRole('button', { name: /Pagar com Mercado Pago/i }));

    await waitFor(() => expect(mockedTransactionService.create).toHaveBeenCalledTimes(2));
    expect(mockedTransactionService.create.mock.calls[1][1]).toBe(firstKey);
    expect(assign).toHaveBeenCalledWith('https://checkout.example.com');
  });

  it('blocks double click while checkout creation is pending', async () => {
    const user = userEvent.setup();
    mockedTransactionService.create.mockReturnValue(new Promise(() => undefined));

    renderApp('/buy');
    await user.type(screen.getByLabelText('Nome'), 'Guest User');
    await user.type(screen.getByLabelText('Telefone'), '(11) 99999-9999');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await screen.findAllByText('R$ 10,00');

    const payButton = screen.getByRole('button', { name: /Pagar com Mercado Pago/i });
    await user.dblClick(payButton);

    expect(mockedTransactionService.create).toHaveBeenCalledTimes(1);
  });

  it('renders approved payment numbers from backend status', async () => {
    mockedTransactionService.getStatus.mockResolvedValue({
      externalReference: 'external-reference',
      recoveryCode: '4821',
      luckyNumbers: ['00042', '12345'],
      participantFlagEmoji: '🇧🇷',
      participantFlagName: 'Brasil',
      quantity: 2,
      status: 'APROVADO',
      totalAmount: '20.00',
    });

    renderApp('/payment-return/success?external_reference=external-reference');

    expect(await screen.findByText('00042')).toBeInTheDocument();
    expect(screen.getByText('12345')).toBeInTheDocument();
    expect(screen.getByText('Sua bandeira')).toBeInTheDocument();
    expect(screen.getAllByRole('img', { name: '🇧🇷' }).length).toBeGreaterThan(0);
    expect(screen.getAllByText('Brasil').length).toBeGreaterThan(0);
    expect(screen.getByText('4821')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Copiar código' })).toBeInTheDocument();
  });

  it('renders previous and current lucky numbers for repeat buyers', async () => {
    mockedTransactionService.getStatus.mockResolvedValue({
      externalReference: 'external-reference',
      recoveryCode: '4821',
      luckyNumbers: ['00042', '12345'],
      previousLuckyNumbers: ['00001', '00002'],
      totalLuckyNumbers: 4,
      participantFlagEmoji: '🇧🇷',
      participantFlagName: 'Brasil',
      quantity: 2,
      status: 'APROVADO',
      totalAmount: '20.00',
    });

    renderApp('/payment-return/success?external_reference=external-reference');

    expect(await screen.findByText('Resumo dos seus números')).toBeInTheDocument();
    expect(screen.getByText('Números adquiridos anteriormente:')).toBeInTheDocument();
    expect(screen.getByText('Números adquiridos agora:')).toBeInTheDocument();
    expect(screen.getByText('Total de números com esta compra:')).toBeInTheDocument();
    const currentTitle = screen.getByRole('region', { name: 'Números adquiridos agora' });
    const previousTitle = screen.getByRole('region', { name: 'Números adquiridos anteriormente' });
    expect(
      currentTitle.compareDocumentPosition(previousTitle) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(screen.getAllByText('2', { selector: 'dd' })).toHaveLength(2);
    expect(screen.getByText('4', { selector: 'dd' })).toBeInTheDocument();
    expect(screen.getByText('00001')).toBeInTheDocument();
    expect(screen.getByText('00002')).toBeInTheDocument();
    expect(screen.getByText('00042')).toBeInTheDocument();
    expect(screen.getByText('12345')).toBeInTheDocument();
  });

  it('renders pdf download for approved payment', async () => {
    mockedTransactionService.getStatus.mockResolvedValue({
      externalReference: 'external-reference',
      recoveryCode: '4821',
      luckyNumbers: ['00042'],
      participantFlagEmoji: '🇧🇷',
      participantFlagName: 'Brasil',
      quantity: 1,
      status: 'APROVADO',
      totalAmount: '10.00',
    });

    renderApp('/payment-return/success?external_reference=external-reference');

    expect(await screen.findByText('Baixe seus números agora')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Baixar PDF/i })).toHaveAttribute(
      'href',
      'http://localhost:8080/transactions/external-reference/lucky-numbers.pdf',
    );
  });

  it('renders pending payment message', async () => {
    mockedTransactionService.getStatus.mockResolvedValue({
      externalReference: 'external-reference',
      recoveryCode: '4821',
      luckyNumbers: [],
      participantFlagEmoji: '🇧🇷',
      participantFlagName: 'Brasil',
      quantity: 1,
      status: 'PENDENTE',
      totalAmount: '10.00',
    });

    renderApp('/payment-return/pending?external_reference=external-reference');

    expect(await screen.findByText('Pagamento pendente')).toBeInTheDocument();
    expect(screen.getByText(/números serão gerados assim que a confirmação/i)).toBeInTheDocument();
    expect(screen.getByText('4821')).toBeInTheDocument();
    expect(screen.getByText(/Este código é único para todas as suas compras/i)).toBeInTheDocument();
    expect(screen.getByText(/Não compartilhe com ninguém/i)).toBeInTheDocument();
  });

  it('keeps an approved payment without numbers in the normal buyer flow and directs contact to admin', async () => {
    mockedTransactionService.getStatus.mockResolvedValue({
      externalReference: 'external-reference',
      recoveryCode: '4821',
      luckyNumbers: [],
      participantFlagEmoji: '🇧🇷',
      participantFlagName: 'Brasil',
      quantity: 2,
      status: 'APROVADO',
      totalAmount: '20.00',
    });

    renderApp('/payment-return/success?external_reference=external-reference');

    expect(await screen.findByText('Pagamento confirmado')).toBeInTheDocument();
    expect(screen.getByText(/Entre em contato com o administrador/i)).toBeInTheDocument();
    expect(screen.queryByText('REVISÃO DE CAPACIDADE')).not.toBeInTheDocument();
  });

  it('recovers lucky numbers by phone and code from the recovery page', async () => {
    const user = userEvent.setup();
    mockedTransactionService.recover.mockResolvedValue({
      externalReference: 'external-reference',
      recoveryCode: '4821',
      luckyNumbers: ['00042', '00090'],
      participantFlagEmoji: '🇧🇷',
      participantFlagName: 'Brasil',
      quantity: 1,
      status: 'APROVADO',
      totalAmount: '10.00',
    });

    renderApp('/recover');

    await user.type(await screen.findByLabelText('Telefone da compra'), '11999999999');
    await user.type(screen.getByLabelText('Código de 4 dígitos'), '4821');
    await user.click(screen.getByRole('button', { name: 'Consultar meus números' }));

    await waitFor(() =>
      expect(mockedTransactionService.recover).toHaveBeenCalledWith({
        phone: '11999999999',
        recoveryCode: '4821',
      }),
    );
    expect(await screen.findByText('00042')).toBeInTheDocument();
    expect(screen.getByText('00090')).toBeInTheDocument();
    expect(screen.getByText('Sua bandeira')).toBeInTheDocument();
    expect(screen.getAllByText('Brasil').length).toBeGreaterThan(0);
    expect(screen.getAllByRole('img', { name: '🇧🇷' }).length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: 'Copiar código' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Baixar PDF/i })).toHaveAttribute(
      'href',
      'http://localhost:8080/transactions/external-reference/lucky-numbers.pdf',
    );
  });

  it('renders a friendly error when external reference is missing', () => {
    renderApp('/payment-return/success');

    expect(screen.getByText('Não foi possível localizar sua compra')).toBeInTheDocument();
  });

  it('redirects protected admin route to login without session', async () => {
    renderApp('/admin');

    expect(
      await screen.findByText('Área Administrativa', {}, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Usuário')).toBeInTheDocument();
  });

  it('logs admin in and renders dashboard', async () => {
    const user = userEvent.setup();
    mockedAuthService.login.mockImplementation(async () => {
      const response = { accessToken: 'jwt-token', expiresIn: 3600, tokenType: 'Bearer' };
      storeAdminSession(createAdminSession(response));
      return response;
    });

    renderApp('/admin/login');

    await user.type(await screen.findByLabelText('Usuário'), 'admin');
    await user.type(screen.getByLabelText('Senha'), 'password');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByText('Painel administrativo')).toBeInTheDocument();
    expect(mockedAuthService.login).toHaveBeenCalledWith({
      username: 'admin',
      password: 'password',
    });
  });

  it('lists admin transactions with phone or name filter', async () => {
    const user = userEvent.setup();
    storeAdminSession(
      createAdminSession({ accessToken: 'jwt-token', expiresIn: 3600, tokenType: 'Bearer' }),
    );

    renderApp('/admin');

    await user.click(await screen.findByRole('button', { name: 'Mostrar valores' }));

    expect(await screen.findByText('Guest User')).toBeInTheDocument();
    expect(screen.getByText('14/08/2026, 18:00')).toBeInTheDocument();
    expect(screen.getByText('00001')).toBeInTheDocument();
    expect(screen.getByText('(11) 99999-9999')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Baixar PDF dos números de Guest User' }),
    ).toBeInTheDocument();

    await user.type(screen.getByLabelText('Buscar por nome ou telefone'), '(11) 99999-9999');
    await user.click(screen.getByRole('button', { name: 'Buscar' }));

    await waitFor(() =>
      expect(mockedAdminTransactionService.list).toHaveBeenLastCalledWith({
        query: '(11) 99999-9999',
        page: 0,
        size: 20,
        sort: 'createdAt,desc',
      }),
    );
  });

  it('sorts admin transactions by the selected order', async () => {
    const user = userEvent.setup();
    storeAdminSession(
      createAdminSession({ accessToken: 'jwt-token', expiresIn: 3600, tokenType: 'Bearer' }),
    );

    renderApp('/admin');

    await screen.findByText('00001');
    await user.selectOptions(screen.getByLabelText('Ordenar por'), 'totalAmount,desc');

    await waitFor(() =>
      expect(mockedAdminTransactionService.list).toHaveBeenLastCalledWith({
        query: '',
        page: 0,
        size: 20,
        sort: 'totalAmount,desc',
      }),
    );
  });

  it('shows global admin metrics and toggles their visibility', async () => {
    const user = userEvent.setup();
    storeAdminSession(
      createAdminSession({ accessToken: 'jwt-token', expiresIn: 3600, tokenType: 'Bearer' }),
    );
    mockedAdminTransactionService.getSummary.mockResolvedValue({
      approvedLuckyNumbers: 99,
      approvedRevenue: '990.00',
      totalTransactions: 42,
    });

    renderApp('/admin');

    expect(await screen.findByText('00001')).toBeInTheDocument();
    expect((await screen.findAllByText('****')).length).toBeGreaterThanOrEqual(7);
    expect(screen.queryByText('Guest User')).not.toBeInTheDocument();
    expect(screen.queryByText('(11) 99999-9999')).not.toBeInTheDocument();
    expect(screen.queryByText('R$ 20,00')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Mostrar valores' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Mostrar valores' }));

    expect(await screen.findByText('42')).toBeInTheDocument();
    expect(screen.getByText('99')).toBeInTheDocument();
    expect(screen.getByText('R$ 990,00')).toBeInTheDocument();
    expect(screen.getByText('Guest User')).toBeInTheDocument();
    expect(screen.getByText('(11) 99999-9999')).toBeInTheDocument();
    expect(screen.getByText('R$ 20,00')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ocultar valores' })).toBeInTheDocument();
  });

  it('expands and collapses transaction lucky numbers above the initial limit', async () => {
    const user = userEvent.setup();
    const luckyNumbers = Array.from({ length: 10 }, (_, index) =>
      String(index + 1).padStart(5, '0'),
    );
    mockedAdminTransactionService.list.mockResolvedValue({
      content: [
        {
          createdAt: '2026-08-14T18:00:00-03:00',
          email: 'guest@example.com',
          externalReference: 'external-reference',
          luckyNumbers,
          name: 'Guest User',
          paymentMethod: 'MERCADO_PAGO',
          phone: '11999999999',
          quantity: 10,
          status: 'APROVADO',
          totalAmount: '100.00',
        },
      ],
      first: true,
      last: true,
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    storeAdminSession(
      createAdminSession({ accessToken: 'jwt-token', expiresIn: 3600, tokenType: 'Bearer' }),
    );

    renderApp('/admin');

    const transactionRow = await screen.findByRole('button', { name: /00008/ });
    expect(screen.getByText('00008')).toBeInTheDocument();
    expect(screen.queryByText('00009')).not.toBeInTheDocument();

    await user.click(transactionRow);

    expect(screen.getByText('00009')).toBeInTheDocument();
    expect(screen.getByText('00010')).toBeInTheDocument();

    await user.click(transactionRow);

    expect(screen.queryByText('00009')).not.toBeInTheDocument();
  });

  it('deletes cash transactions from admin list', async () => {
    const user = userEvent.setup();
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    storeAdminSession(
      createAdminSession({ accessToken: 'jwt-token', expiresIn: 3600, tokenType: 'Bearer' }),
    );
    mockedAdminTransactionService.list.mockResolvedValue({
      content: [
        {
          createdAt: '2026-08-14T18:00:00-03:00',
          email: null,
          externalReference: 'cash-reference',
          luckyNumbers: ['00077'],
          name: 'Cash Guest',
          paymentMethod: 'CASH',
          phone: '11999999999',
          quantity: 1,
          status: 'APROVADO',
          totalAmount: '10.00',
        },
      ],
      first: true,
      last: true,
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    mockedAdminTransactionService.deleteCashTransaction.mockResolvedValue();

    try {
      renderApp('/admin');

      await user.click(await screen.findByRole('button', { name: 'Excluir transação' }));

      expect(confirm).toHaveBeenCalledWith('Excluir esta transação em dinheiro?');
      await waitFor(() =>
        expect(mockedAdminTransactionService.deleteCashTransaction).toHaveBeenCalledWith(
          'cash-reference',
        ),
      );
    } finally {
      confirm.mockRestore();
    }
  });

  it('allows the admin to resolve a capacity review without exposing it publicly', async () => {
    const user = userEvent.setup();
    storeAdminSession(
      createAdminSession({ accessToken: 'jwt-token', expiresIn: 3600, tokenType: 'Bearer' }),
    );
    mockedAdminTransactionService.list.mockResolvedValue({
      content: [
        {
          capacityReviewStatus: 'PENDING',
          createdAt: '2026-08-14T18:00:00-03:00',
          email: null,
          externalReference: 'review-reference',
          luckyNumbers: [],
          name: 'Review Guest',
          paymentMethod: 'MERCADO_PAGO',
          phone: '11999999999',
          quantity: 2,
          status: 'APROVADO',
          totalAmount: '20.00',
        },
      ],
      first: true,
      last: true,
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    mockedAdminTransactionService.resolveCapacityReview.mockResolvedValue();

    renderApp('/admin');

    expect(await screen.findByText('REVISÃO DE CAPACIDADE')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Contribuição mantida sem números' }));

    await waitFor(() =>
      expect(mockedAdminTransactionService.resolveCapacityReview).toHaveBeenCalledWith(
        'review-reference',
        'CONTRIBUTION_WITHOUT_NUMBERS',
      ),
    );
  });

  it('registers an admin cash payment and shows pdf link', async () => {
    const user = userEvent.setup();
    const luckyNumbers = Array.from({ length: 10 }, (_, index) =>
      String(index + 1).padStart(5, '0'),
    );
    storeAdminSession(
      createAdminSession({ accessToken: 'jwt-token', expiresIn: 3600, tokenType: 'Bearer' }),
    );
    mockedAdminTransactionService.createCashTransaction.mockResolvedValue({
      email: null,
      externalReference: 'cash-reference',
      luckyNumbers,
      name: 'Cash Guest',
      paymentMethod: 'CASH',
      phone: '11999999999',
      participantFlagEmoji: '🇧🇷',
      participantFlagName: 'Brasil',
      previousLuckyNumbers: ['00090', '00091'],
      quantity: 1,
      recoveryCode: '4821',
      status: 'APROVADO',
      totalAmount: '10.00',
      totalLuckyNumbers: 12,
    });
    mockedTransactionService.getLuckyNumbersPdfUrl.mockReturnValue(
      'http://localhost:8080/transactions/cash-reference/lucky-numbers.pdf',
    );

    renderApp('/admin/cash-payment');

    await user.type(await screen.findByLabelText('Nome'), 'Cash Guest');
    expect(screen.queryByLabelText('E-mail (opcional)')).not.toBeInTheDocument();
    await user.type(screen.getByLabelText('Telefone'), '11999999999');
    expect(screen.getByLabelText('Telefone')).toHaveValue('(11) 99999-9999');
    await user.clear(screen.getByLabelText('Quantidade'));
    await user.type(screen.getByLabelText('Quantidade'), '10');
    await user.click(screen.getByRole('button', { name: /Confirmar pagamento/i }));

    await waitFor(() =>
      expect(mockedAdminTransactionService.createCashTransaction).toHaveBeenCalledWith(
        {
          name: 'Cash Guest',
          phone: '11999999999',
          quantity: 10,
        },
        expect.any(String),
      ),
    );
    expect(await screen.findByText('Números adquiridos anteriormente:')).toBeInTheDocument();
    expect(screen.getByText('Números adquiridos agora:')).toBeInTheDocument();
    expect(screen.getByText('Total de números com esta compra:')).toBeInTheDocument();
    expect(screen.getByText('12', { selector: 'dd' })).toBeInTheDocument();
    expect(screen.getByText('Bandeira do participante')).toBeInTheDocument();
    expect(screen.getByText('Brasil')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '🇧🇷' })).toBeInTheDocument();
    expect(await screen.findByText('00008')).toBeInTheDocument();
    expect(screen.queryByText('00009')).not.toBeInTheDocument();
    expect(screen.getByText('00090')).toBeInTheDocument();
    expect(screen.getByText('00091')).toBeInTheDocument();
    expect(screen.getByText('4821')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /00001/ }));

    expect(screen.getByText('00009')).toBeInTheDocument();
    expect(screen.getByText('00010')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Baixar PDF/i })).toHaveAttribute(
      'href',
      'http://localhost:8080/transactions/cash-reference/lucky-numbers.pdf',
    );
  });

  it('lists gift messages for admin', async () => {
    storeAdminSession(
      createAdminSession({ accessToken: 'jwt-token', expiresIn: 3600, tokenType: 'Bearer' }),
    );

    renderApp('/admin/messages');

    expect(await screen.findByText('Mensagens')).toBeInTheDocument();
    expect(await screen.findByText('Guest User')).toBeInTheDocument();
    expect(screen.getByText('Felicidades ao casal!')).toBeInTheDocument();
    expect(mockedAdminTransactionService.listGiftMessages).toHaveBeenCalledWith({
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
    });
  });

  it('updates raffle unit price from admin settings', async () => {
    const user = userEvent.setup();
    storeAdminSession(
      createAdminSession({ accessToken: 'jwt-token', expiresIn: 3600, tokenType: 'Bearer' }),
    );
    mockedRaffleConfigService.updateUnitPrice.mockResolvedValue({
      scheduledDrawAt: null,
      unitPrice: '15.00',
      updatedAt: '2026-08-14T18:05:00-03:00',
      combos: [],
    });

    renderApp('/admin/settings');

    expect(await screen.findByText('Preço unitário')).toBeInTheDocument();
    expect(await screen.findByText('R$ 10,00')).toBeInTheDocument();

    await user.clear(screen.getByLabelText('Valor por número'));
    await user.type(screen.getByLabelText('Valor por número'), '15');
    await user.click(screen.getByRole('button', { name: /Salvar preço/i }));

    await waitFor(() =>
      expect(mockedRaffleConfigService.updateUnitPrice).toHaveBeenCalledWith({
        unitPrice: '15.00',
      }),
    );
    expect(await screen.findByText('Preço atualizado com sucesso.')).toBeInTheDocument();
    expect(screen.getByText('R$ 15,00')).toBeInTheDocument();
  });

  it('updates combo price, status, order, and configurable highlights without editing quantity', async () => {
    const user = userEvent.setup();
    storeAdminSession(
      createAdminSession({ accessToken: 'jwt-token', expiresIn: 3600, tokenType: 'Bearer' }),
    );
    const combo = {
      active: true,
      averagePricePerNumber: '44.00',
      discountPercent: '12.00',
      displayOrder: 3,
      highlightBestValue: false,
      highlightMostChosen: false,
      id: 3,
      price: '880.00',
      quantity: 20,
      regularPrice: '1000.00',
      savingsAmount: '120.00',
    };
    mockedRaffleConfigService.getConfig.mockResolvedValue({
      combos: [combo],
      scheduledDrawAt: null,
      unitPrice: '50.00',
      updatedAt: '2026-08-14T18:00:00-03:00',
    });
    mockedRaffleConfigService.updateCombo.mockResolvedValue({
      combos: [{ ...combo, displayOrder: 7, highlightMostChosen: true, price: '870.00' }],
      scheduledDrawAt: null,
      unitPrice: '50.00',
      updatedAt: '2026-08-14T18:05:00-03:00',
    });

    renderApp('/admin/settings');
    expect(await screen.findByText('20 números')).toBeInTheDocument();
    expect(screen.queryByLabelText('Quantidade')).not.toBeInTheDocument();
    await user.clear(screen.getByLabelText('Preço'));
    await user.type(screen.getByLabelText('Preço'), '870');
    await user.clear(screen.getByLabelText('Ordem'));
    await user.type(screen.getByLabelText('Ordem'), '7');
    await user.click(screen.getByLabelText('Mais escolhido'));
    await user.click(screen.getByRole('button', { name: 'Salvar combo' }));

    await waitFor(() =>
      expect(mockedRaffleConfigService.updateCombo).toHaveBeenCalledWith(3, {
        active: true,
        displayOrder: 7,
        highlightBestValue: false,
        highlightMostChosen: true,
        price: '870.00',
      }),
    );
  });

  it('updates scheduled draw date from admin settings', async () => {
    const user = userEvent.setup();
    storeAdminSession(
      createAdminSession({ accessToken: 'jwt-token', expiresIn: 3600, tokenType: 'Bearer' }),
    );
    mockedRaffleConfigService.updateScheduledDrawAt.mockResolvedValue({
      scheduledDrawAt: '2026-09-05T23:00:00.000Z',
      unitPrice: '10.00',
      updatedAt: '2026-08-14T18:05:00-03:00',
      combos: [],
    });

    renderApp('/admin/settings');

    expect(await screen.findByText('Data do sorteio')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Data e horário'), '2026-09-05T20:00');
    await user.click(screen.getByRole('button', { name: /Salvar data/i }));

    await waitFor(() =>
      expect(mockedRaffleConfigService.updateScheduledDrawAt).toHaveBeenCalledWith({
        scheduledDrawAt: '2026-09-05T23:00:00.000Z',
      }),
    );
    expect(await screen.findByText('Data do sorteio atualizada com sucesso.')).toBeInTheDocument();
  });

  it('renders existing raffle result and keeps draw action available', async () => {
    storeAdminSession(
      createAdminSession({ accessToken: 'jwt-token', expiresIn: 3600, tokenType: 'Bearer' }),
    );
    mockedRaffleService.getResult.mockResolvedValue({
      drawnAt: '2026-07-30T12:00:00Z',
      winnerName: 'Winner Guest',
      winningNumber: '00042',
    });

    renderApp('/admin/draw');

    expect(await screen.findByText('00042')).toBeInTheDocument();
    expect(screen.getByText('Winner Guest')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sortear novamente' })).toBeInTheDocument();
    expect(mockedRaffleService.draw).not.toHaveBeenCalled();
  });

  it('shows suspense and runs raffle draw when no result exists', async () => {
    const user = userEvent.setup();
    storeAdminSession(
      createAdminSession({ accessToken: 'jwt-token', expiresIn: 3600, tokenType: 'Bearer' }),
    );
    mockedRaffleService.getResult.mockRejectedValue({ status: 404 });
    mockedRaffleService.draw.mockResolvedValue({
      drawnAt: '2026-07-30T12:00:00Z',
      winnerName: 'Winner Guest',
      winningNumber: '00042',
    });

    renderApp('/admin/draw');

    await user.click(await screen.findByRole('button', { name: 'Sortear vencedor' }));
    await user.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByText('Sorteando entre os números')).toBeInTheDocument();
    expect(mockedRaffleService.getEligibleNumbers).toHaveBeenCalledTimes(1);

    await waitFor(() => expect(mockedRaffleService.draw).toHaveBeenCalledTimes(1), {
      timeout: 7000,
    });
    expect(await screen.findByText('00042')).toBeInTheDocument();
  }, 8500);
});
