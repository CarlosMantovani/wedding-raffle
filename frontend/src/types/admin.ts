import type { PaymentStatus } from './transaction';

export type PaymentMethod = 'MERCADO_PAGO' | 'CASH';
export type CapacityReviewStatus = 'PENDING' | 'REFUND_COMPLETED' | 'CONTRIBUTION_WITHOUT_NUMBERS';
export type CapacityReviewDecision = Exclude<CapacityReviewStatus, 'PENDING'>;

export interface AdminTransactionResponse {
  externalReference: string;
  createdAt: string;
  name: string;
  phone: string;
  email: string | null;
  paymentMethod: PaymentMethod;
  capacityReviewStatus?: CapacityReviewStatus | null;
  quantity: number;
  totalAmount: string;
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
  totalAmount: string;
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
}

export interface UnitPriceUpdateRequest {
  unitPrice: string;
}

export interface ScheduledDrawAtUpdateRequest {
  scheduledDrawAt: string;
}
