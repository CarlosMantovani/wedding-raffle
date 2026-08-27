import { apiClient } from '../config/apiClient';
import { adminTransactionService } from './adminTransactionService';
import { transactionService } from './transactionService';

vi.mock('../config/apiClient', () => ({
  apiClient: {
    defaults: { baseURL: 'http://localhost:8080' },
    post: vi.fn(),
  },
}));

const mockedApiClient = vi.mocked(apiClient);

describe('purchase services', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('sends the checkout idempotency key in the request header', async () => {
    mockedApiClient.post.mockResolvedValue({
      data: { checkoutUrl: 'https://checkout.example.com' },
    });
    const request = { cpf: '52998224725', name: 'Guest User', phone: '11999999999', quantity: 2 };

    await transactionService.create(request, 'checkout-key-123');

    expect(mockedApiClient.post).toHaveBeenCalledWith('/transactions', request, {
      headers: { 'Idempotency-Key': 'checkout-key-123' },
    });
  });

  it('sends optional checkout email unchanged when present', async () => {
    mockedApiClient.post.mockResolvedValue({
      data: { checkoutUrl: 'https://checkout.example.com' },
    });
    const request = {
      cpf: '52998224725',
      email: 'guest@example.com',
      name: 'Guest User',
      phone: '11999999999',
      quantity: 2,
    };

    await transactionService.create(request, 'checkout-key-123');

    expect(mockedApiClient.post).toHaveBeenCalledWith('/transactions', request, {
      headers: { 'Idempotency-Key': 'checkout-key-123' },
    });
  });

  it('sends the optional Mercado Pago Device ID in the checkout request', async () => {
    mockedApiClient.post.mockResolvedValue({
      data: { checkoutUrl: 'https://checkout.example.com' },
    });
    const request = {
      cpf: '52998224725',
      deviceId: 'device-session-123',
      name: 'Guest User',
      phone: '11999999999',
      quantity: 2,
    };

    await transactionService.create(request, 'checkout-key-123');

    expect(mockedApiClient.post).toHaveBeenCalledWith('/transactions', request, {
      headers: { 'Idempotency-Key': 'checkout-key-123' },
    });
  });

  it('sends the cash registration idempotency key in the request header', async () => {
    mockedApiClient.post.mockResolvedValue({ data: { externalReference: 'cash-reference' } });
    const request = { name: 'Cash Guest', phone: '11999999999', quantity: 2 };

    await adminTransactionService.createCashTransaction(request, 'cash-key-123');

    expect(mockedApiClient.post).toHaveBeenCalledWith('/transactions/cash', request, {
      headers: { 'Idempotency-Key': 'cash-key-123' },
    });
  });
});
