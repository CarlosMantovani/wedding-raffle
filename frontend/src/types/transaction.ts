export type PaymentStatus =
  'PENDENTE' | 'APROVADO' | 'REJEITADO' | 'CANCELADO' | 'ESTORNADO' | 'CHARGEBACK' | 'EM_MEDIACAO';

export interface TransactionQuoteRequest {
  name: string;
  phone: string;
  email?: string;
  quantity: number;
  comboId?: number;
}

export interface RaffleComboResponse {
  id: number;
  quantity: number;
  price: string;
  active: boolean;
  displayOrder: number;
  highlightMostChosen: boolean;
  highlightBestValue: boolean;
  regularPrice: string;
  savingsAmount: string;
  discountPercent: string;
  averagePricePerNumber: string;
}

export interface TransactionQuoteResponse {
  name: string;
  phone: string;
  quantity: number;
  unitPrice: string;
  totalAmount: string;
  comboId: number | null;
  availableCombos: RaffleComboResponse[];
}

export interface TransactionCreateRequest {
  name: string;
  phone: string;
  cpf: string;
  email?: string;
  giftMessage?: string;
  quantity: number;
  comboId?: number;
  deviceId?: string;
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
  participantFlagName: string | null;
  participantFlagEmoji: string | null;
  luckyNumbers: string[];
  previousLuckyNumbers?: string[];
  totalLuckyNumbers?: number;
  checkoutUrl?: string | null;
}
