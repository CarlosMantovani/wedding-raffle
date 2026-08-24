import { apiClient } from '../config/apiClient';
import type {
  AdminTransactionResponse,
  AdminTransactionSummaryResponse,
  AdminGiftMessageResponse,
  CapacityReviewDecision,
  CashTransactionCreateRequest,
  CashTransactionCreateResponse,
} from '../types/admin';
import type { PageResponse } from '../types/page';

export interface AdminTransactionListParams {
  query?: string;
  page: number;
  size: number;
  sort?: string;
}

export const adminTransactionService = {
  async getSummary(): Promise<AdminTransactionSummaryResponse> {
    const response = await apiClient.get<AdminTransactionSummaryResponse>('/transactions/summary');
    return response.data;
  },

  async list(params: AdminTransactionListParams): Promise<PageResponse<AdminTransactionResponse>> {
    const response = await apiClient.get<PageResponse<AdminTransactionResponse>>('/transactions', {
      params: {
        query: params.query || undefined,
        page: params.page,
        size: params.size,
        sort: params.sort,
      },
    });

    return response.data;
  },

  async listGiftMessages(params: Omit<AdminTransactionListParams, 'query'>): Promise<PageResponse<AdminGiftMessageResponse>> {
    const response = await apiClient.get<PageResponse<AdminGiftMessageResponse>>('/transactions/messages', {
      params: {
        page: params.page,
        size: params.size,
        sort: params.sort,
      },
    });

    return response.data;
  },

  async createCashTransaction(
    request: CashTransactionCreateRequest,
    idempotencyKey: string,
  ): Promise<CashTransactionCreateResponse> {
    const response = await apiClient.post<CashTransactionCreateResponse>(
      '/transactions/cash',
      request,
      {
        headers: { 'Idempotency-Key': idempotencyKey },
      },
    );
    return response.data;
  },

  async deleteCashTransaction(externalReference: string): Promise<void> {
    await apiClient.delete(`/transactions/${externalReference}`);
  },

  async resolveCapacityReview(
    externalReference: string,
    decision: CapacityReviewDecision,
  ): Promise<void> {
    await apiClient.put(`/transactions/${externalReference}/capacity-review`, { decision });
  },

  async getParticipantLuckyNumbersPdf(externalReference: string): Promise<Blob> {
    const response = await apiClient.get<Blob>(
      `/transactions/${externalReference}/participant-lucky-numbers.pdf`,
      {
        responseType: 'blob',
      },
    );
    return response.data;
  },
};
