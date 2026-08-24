import { apiClient } from '../config/apiClient';
import type {
  TransactionCreateRequest,
  TransactionCreateResponse,
  TransactionQuoteRequest,
  TransactionQuoteResponse,
  TransactionRecoveryRequest,
  TransactionStatusResponse,
} from '../types/transaction';

export const transactionService = {
  async quote(request: TransactionQuoteRequest): Promise<TransactionQuoteResponse> {
    const response = await apiClient.post<TransactionQuoteResponse>('/transactions/quote', request);
    return response.data;
  },

  async create(
    request: TransactionCreateRequest,
    idempotencyKey: string,
  ): Promise<TransactionCreateResponse> {
    const response = await apiClient.post<TransactionCreateResponse>('/transactions', request, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
    return response.data;
  },

  async getStatus(
    externalReference: string,
    signal?: AbortSignal,
  ): Promise<TransactionStatusResponse> {
    const response = await apiClient.get<TransactionStatusResponse>(
      `/transactions/${externalReference}/status`,
      { signal },
    );
    return response.data;
  },

  async recover(request: TransactionRecoveryRequest): Promise<TransactionStatusResponse> {
    const response = await apiClient.post<TransactionStatusResponse>(
      '/transactions/recovery',
      request,
    );
    return response.data;
  },

  getLuckyNumbersPdfUrl(externalReference: string): string {
    return `${apiClient.defaults.baseURL}/transactions/${externalReference}/lucky-numbers.pdf`;
  },
};
