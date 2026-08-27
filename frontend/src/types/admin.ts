import type { PaymentStatus, RaffleComboResponse } from './transaction';

export type PaymentMethod = 'MERCADO_PAGO' | 'CASH';
export type CapacityReviewStatus = 'PENDING' | 'REFUND_COMPLETED' | 'CONTRIBUTION_WITHOUT_NUMBERS';
export type CapacityReviewDecision = Exclude<CapacityReviewStatus, 'PENDING'>;

export interface AdminTransactionResponse {
  externalReference: string;
  createdAt: string;
  name: string;
  phone: string;
  email: string | null;
  giftMessage?: string | null;
  paymentMethod: PaymentMethod;
  capacityReviewStatus?: CapacityReviewStatus | null;
  quantity: number;
  totalAmount: string | null;
  status: PaymentStatus;
  luckyNumbers: string[];
}

export interface AdminTransactionSummaryResponse {
  totalTransactions: number;
  approvedLuckyNumbers: number;
  approvedRevenue: string;
}

export interface CashTransactionCreateRequest {
  name: string;
  phone: string;
  email?: string;
  giftMessage?: string;
  quantity: number;
}

export interface CashTransactionCreateResponse {
  externalReference: string;
  recoveryCode: string;
  name: string;
  phone: string;
  email: string | null;
  paymentMethod: PaymentMethod;
  quantity: number;
  totalAmount: string | null;
  status: PaymentStatus;
  participantFlagName?: string | null;
  participantFlagEmoji?: string | null;
  luckyNumbers: string[];
  previousLuckyNumbers?: string[];
  totalLuckyNumbers?: number;
}

export interface RaffleDrawResponse {
  winningNumber: string;
  winnerName: string;
  drawnAt: string;
  participantFlagName?: string | null;
  participantFlagEmoji?: string | null;
}

export interface RaffleCandidateResponse {
  luckyNumber: string;
  participantFlagName: string;
  participantFlagEmoji: string;
}

export interface RaffleConfigResponse {
  unitPrice: string;
  scheduledDrawAt: string | null;
  updatedAt: string | null;
  combos: RaffleComboResponse[];
}

export interface UnitPriceUpdateRequest {
  unitPrice: string;
}

export interface ScheduledDrawAtUpdateRequest {
  scheduledDrawAt: string;
}

export interface AdminGiftMessageResponse {
  externalReference: string;
  createdAt: string;
  name: string;
  giftMessage: string;
}

export interface RaffleComboUpdateRequest {
  price: string;
  active: boolean;
  displayOrder: number;
  highlightMostChosen: boolean;
  highlightBestValue: boolean;
}
