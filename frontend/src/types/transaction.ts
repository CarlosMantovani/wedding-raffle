export type PaymentStatus = 'PENDENTE' | 'APROVADO' | 'REJEITADO' | 'CANCELADO' | 'ESTORNADO' | 'CHARGEBACK' | 'EM_MEDIACAO';

export interface TransactionQuoteRequest {
  name: string;
  phone: string;
  quantity: number;
}

export interface TransactionQuoteResponse {
  name: string;
  phone: string;
  quantity: number;
  unitPrice: string;
  totalAmount: string;
}

export interface TransactionCreateRequest {
  name: string;
  phone: string;
  giftMessage?: string;
  quantity: number;
}

export interface TransactionCreateResponse {
  externalReference: string;
  recoveryCode: string;
  preferenceId: string;
  checkoutUrl: string;
}

export interface TransactionRecoveryRequest {
  phone: string;
  recoveryCode: string;
}

export interface TransactionStatusResponse {
  externalReference: string;
  recoveryCode: string;
  status: PaymentStatus;
  quantity: number;
  totalAmount: string;
  participantFlagName: string;
  participantFlagEmoji: string;
  luckyNumbers: string[];
  previousLuckyNumbers?: string[];
  totalLuckyNumbers?: number;
}
