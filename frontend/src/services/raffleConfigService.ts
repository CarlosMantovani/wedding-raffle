import { apiClient } from '../config/apiClient';
import type {
  RaffleComboUpdateRequest,
  RaffleConfigResponse,
  ScheduledDrawAtUpdateRequest,
  UnitPriceUpdateRequest,
  WeddingEventAtUpdateRequest,
} from '../types/admin';

export const raffleConfigService = {
  async getConfig(): Promise<RaffleConfigResponse> {
    const response = await apiClient.get<RaffleConfigResponse>('/admin/raffle-config');
    return response.data;
  },

  async updateUnitPrice(request: UnitPriceUpdateRequest): Promise<RaffleConfigResponse> {
    const response = await apiClient.put<RaffleConfigResponse>(
      '/admin/raffle-config/unit-price',
      request,
    );
    return response.data;
  },

  async updateScheduledDrawAt(
    request: ScheduledDrawAtUpdateRequest,
  ): Promise<RaffleConfigResponse> {
    const response = await apiClient.put<RaffleConfigResponse>(
      '/admin/raffle-config/scheduled-at',
      request,
    );
    return response.data;
  },

  async updateWeddingEventAt(request: WeddingEventAtUpdateRequest): Promise<RaffleConfigResponse> {
    const response = await apiClient.put<RaffleConfigResponse>(
      '/admin/raffle-config/wedding-event-at',
      request,
    );
    return response.data;
  },

  async updateCombo(
    comboId: number,
    request: RaffleComboUpdateRequest,
  ): Promise<RaffleConfigResponse> {
    const response = await apiClient.put<RaffleConfigResponse>(
      `/admin/raffle-config/combos/${comboId}`,
      request,
    );
    return response.data;
  },
};
