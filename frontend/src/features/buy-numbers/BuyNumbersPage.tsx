import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { ArrowLeft, CreditCard, Minus, Plus, Search } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';

import { BrandMark, GoldDivider } from '../../components/brand/BrandMark';
import { StepProgress } from '../../components/brand/StepProgress';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { FlagEmoji } from '../../components/ui/FlagEmoji';
import { TextInput } from '../../components/ui/TextInput';
import { publicMessages } from '../../content/messages';
import { homeService } from '../../services/homeService';
import { transactionService } from '../../services/transactionService';
import type { RaffleResult } from '../../types/home';
import type { RaffleComboResponse, TransactionStatusResponse } from '../../types/transaction';
import { isPastDateTime } from '../../utils/dateTime';
import { formatCurrency } from '../../utils/formatters';
import { clearIdempotencyKey, getOrCreateIdempotencyKey } from '../../utils/idempotency';
import { formatPhoneNumber, normalizePhoneNumber } from '../../utils/phone';
import {
  clearRecentCheckout,
  readRecentCheckout,
  saveRecentCheckout,
  type RecentCheckout,
} from '../../utils/recentCheckout';
import { FlagRankingPanel } from '../flag-ranking/FlagRankingPanel';
import { CountdownPanel } from './CountdownPanel';
import { buyerSchema, type BuyerFormData } from './schemas';

const CHECKOUT_IDEMPOTENCY_ACTION = 'mercado-pago-checkout';
const GIFT_MESSAGE_MAX_LENGTH = 280;
type PurchaseStep = 'quantity' | 'message' | 'review';
type BuyerData = { email?: string; name: string; phone: string };

export function BuyNumbersPage({ showBackLink = false }: { showBackLink?: boolean }) {
  const [buyer, setBuyer] = useState<BuyerData | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [giftMessage, setGiftMessage] = useState('');
  const [selectedComboId, setSelectedComboId] = useState<number | null>(null);
  const [purchaseStep, setPurchaseStep] = useState<PurchaseStep>('quantity');
  const [isCheckoutConfirmationOpen, setIsCheckoutConfirmationOpen] = useState(false);
  const [isRedirectingToCheckout, setIsRedirectingToCheckout] = useState(false);
  const [recentCheckout, setRecentCheckout] = useState<RecentCheckout | null>(() =>
    readRecentCheckout(),
  );
  const [, setTick] = useState(0);

  const {
    formState: { errors, isValid },
    handleSubmit,
    register,
  } = useForm<BuyerFormData>({
    defaultValues: { email: '', name: '', phone: '' },
    mode: 'onChange',
    resolver: zodResolver(buyerSchema),
  });

  const homeSummaryQuery = useQuery({
    queryKey: ['home-summary'],
    queryFn: homeService.getSummary,
  });
  const scheduledDrawAt = homeSummaryQuery.data?.scheduledDrawAt ?? null;
  const isDrawClosed = isPastDateTime(scheduledDrawAt);
  const quoteQuery = useQuery({
    enabled: Boolean(buyer) && !isDrawClosed,
    placeholderData: (previousData) => previousData,
    queryKey: ['transaction-pricing', buyer],
    queryFn: () =>
      transactionService.quote({
        ...buyer!,
        quantity: 1,
      }),
    staleTime: 5 * 60 * 1000,
  });
  const recentCheckoutStatusQuery = useQuery({
    enabled: Boolean(recentCheckout),
    queryKey: ['recent-checkout-status', recentCheckout?.externalReference],
    queryFn: ({ signal }) =>
      transactionService.getStatus(recentCheckout?.externalReference ?? '', undefined, signal),
  });

  useEffect(() => {
    if (!scheduledDrawAt) return undefined;

    const intervalId = window.setInterval(() => setTick((current) => current + 1), 1000);
    return () => window.clearInterval(intervalId);
  }, [scheduledDrawAt]);

  useEffect(() => {
    const status = recentCheckoutStatusQuery.data?.status;
    if (!recentCheckout || !status) return undefined;

    if (status !== 'PENDENTE' && status !== 'APROVADO') {
      clearRecentCheckout(recentCheckout.externalReference);
      const timeoutId = window.setTimeout(() => setRecentCheckout(null), 0);
      return () => window.clearTimeout(timeoutId);
    }
    return undefined;
  }, [recentCheckout, recentCheckoutStatusQuery.data?.status]);

  useEffect(() => {
    const syncRecentCheckout = () => {
      setRecentCheckout(readRecentCheckout());
    };

    window.addEventListener('focus', syncRecentCheckout);
    window.addEventListener('pageshow', syncRecentCheckout);
    document.addEventListener('visibilitychange', syncRecentCheckout);

    return () => {
      window.removeEventListener('focus', syncRecentCheckout);
      window.removeEventListener('pageshow', syncRecentCheckout);
      document.removeEventListener('visibilitychange', syncRecentCheckout);
    };
  }, []);

  const createTransactionMutation = useMutation({
    mutationFn: ({ idempotencyKey, request }: PurchaseSubmission) =>
      transactionService.create(request, idempotencyKey),
    onSuccess: (response, submission) => {
      setIsRedirectingToCheckout(true);
      saveRecentCheckout(response);
      setRecentCheckout(readRecentCheckout());
      clearIdempotencyKey(CHECKOUT_IDEMPOTENCY_ACTION, submission.idempotencyKey);
      window.location.assign(response.checkoutUrl);
    },
  });

  const onSubmitBuyer = (data: BuyerFormData) => {
    const email = data.email.trim();
    setBuyer({
      ...(email ? { email } : {}),
      name: data.name.trim(),
      phone: normalizePhoneNumber(data.phone),
    });
    setPurchaseStep('quantity');
  };

  const decreaseQuantity = () => {
    setSelectedComboId(null);
    setQuantity((current) => Math.max(1, current - 1));
  };
  const increaseQuantity = () => {
    setSelectedComboId(null);
    setQuantity((current) => current + 1);
  };

  const changeQuantity = (value: string) => {
    const nextQuantity = Number(value);
    if (!Number.isInteger(nextQuantity) || nextQuantity < 1) return;
    setSelectedComboId(null);
    setQuantity(nextQuantity);
  };

  const selectCombo = (combo: RaffleComboResponse) => {
    setQuantity(combo.quantity);
    setSelectedComboId(combo.id);
  };

  const handlePay = () => {
    if (!buyer || isDrawClosed || createTransactionMutation.isPending || isRedirectingToCheckout)
      return;
    const trimmedGiftMessage = giftMessage.trim();
    if (trimmedGiftMessage.length > GIFT_MESSAGE_MAX_LENGTH) return;
    const request = {
      ...buyer,
      ...(trimmedGiftMessage ? { giftMessage: trimmedGiftMessage } : {}),
      quantity,
      ...(selectedCombo ? { comboId: selectedCombo.id } : {}),
    };
    createTransactionMutation.mutate({
      idempotencyKey: getOrCreateIdempotencyKey(CHECKOUT_IDEMPOTENCY_ACTION, request),
      request,
    });
  };

  const openCheckoutConfirmation = () => {
    if (!canContinueFromQuantity || giftMessageTooLong) return;
    setIsCheckoutConfirmationOpen(true);
  };

  const currentStep: 1 | 2 | 3 | 4 = !buyer
    ? 1
    : purchaseStep === 'quantity'
      ? 2
      : purchaseStep === 'message'
        ? 3
        : 4;
  const unitPrice = quoteQuery.data?.unitPrice;
  const availableCombos = quoteQuery.data?.availableCombos ?? [];
  const selectedCombo =
    availableCombos.find((combo) => combo.id === selectedComboId && combo.quantity === quantity) ??
    availableCombos.find((combo) => combo.quantity === quantity);
  const totalAmount =
    selectedCombo?.price ?? (unitPrice ? multiplyMoney(unitPrice, quantity) : undefined);
  const canContinueFromQuantity = Boolean(totalAmount) && !quoteQuery.isLoading;
  const giftMessageTooLong = giftMessage.trim().length > GIFT_MESSAGE_MAX_LENGTH;
  const isCheckoutBusy = createTransactionMutation.isPending || isRedirectingToCheckout;

  return (
    <main className="min-h-screen bg-cream px-6 pb-16 pt-10 text-charcoal">
      {isRedirectingToCheckout ? <CheckoutRedirectOverlay /> : null}
      <div className="mx-auto flex w-full max-w-[480px] flex-col gap-7">
        <header className="text-center">
          <BrandMark />
            <p className="mt-2 font-serif text-2xl font-bold leading-tight text-charcoal">
                Presente <span className="italic text-gold">Premiado</span>
            </p>
            <p className="mx-auto mt-2 max-w-xs text-sm leading-relaxed text-warm-gray">
                Participe do sorteio e faça parte deste presente especial para o casal.
            </p>
          <div className="mt-6">
            <GoldDivider />
          </div>
        </header>

        {showBackLink ? (
          <a
            className="inline-flex min-h-11 items-center justify-center gap-2 self-center rounded-lg border border-green px-4 py-2 text-sm font-bold text-green transition hover:bg-ivory-deep focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green"
            href="/"
          >
            <ArrowLeft aria-hidden="true" className="h-4 w-4" />
            Voltar
          </a>
        ) : null}

        {!isDrawClosed ? <CountdownPanel scheduledDrawAt={scheduledDrawAt} /> : null}

        {recentCheckout && !isRedirectingToCheckout ? (
          <RecentCheckoutNotice
            checkout={recentCheckout}
            isError={recentCheckoutStatusQuery.isError}
            isLoading={recentCheckoutStatusQuery.isLoading}
            transaction={recentCheckoutStatusQuery.data}
          />
        ) : null}

        {!isDrawClosed ? <StepProgress currentStep={currentStep} /> : null}

        {isDrawClosed ? (
          <Card className="border border-line bg-white/90 text-center shadow-none">
            <h1 className="font-serif text-2xl font-bold text-green">Sorteio encerrado</h1>
            <p className="mx-auto mt-3 max-w-xs text-sm leading-relaxed text-warm-gray">
              {publicMessages.drawClosed}
            </p>
          </Card>
        ) : !buyer ? (
          <Card>
            <form className="space-y-5" onSubmit={handleSubmit(onSubmitBuyer)}>
              <div>
                <h1 className="font-serif text-xl font-semibold text-charcoal">Vamos começar!</h1>
                <p className="mt-1 text-sm text-warm-gray">
                  Preencha seus dados e escolha a quantidade de números da sorte.
                </p>
              </div>

              <TextInput
                autoComplete="name"
                error={errors.name?.message}
                id="buyer-name"
                label="Nome"
                placeholder="Seu nome"
                {...register('name')}
              />

              <TextInput
                autoComplete="tel"
                error={errors.phone?.message}
                helper="Use um telefone com DDD."
                id="buyer-phone"
                inputMode="tel"
                label="Telefone"
                maxLength={15}
                placeholder="(11) 99999-9999"
                type="tel"
                {...register('phone', {
                  onChange: (event) => {
                    event.target.value = formatPhoneNumber(event.target.value);
                  },
                })}
              />

              <TextInput
                autoComplete="email"
                error={errors.email?.message}
                helper="E-mail opcional. Ele será utilizado apenas para o envio do comprovante de pagamento."
                id="buyer-email"
                inputMode="email"
                label="E-mail (opcional)"
                placeholder="seu@email.com"
                type="email"
                {...register('email')}
              />

              <Button disabled={!isValid || homeSummaryQuery.isLoading} type="submit">
                Continuar
              </Button>
              <a
                className="inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-lg border border-green bg-white/70 px-4 py-2 text-sm font-bold text-green transition hover:bg-ivory-deep focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green"
                href="/recover"
              >
                <Search aria-hidden="true" className="h-4 w-4" />
                Consultar meus números
              </a>
            </form>
          </Card>
        ) : purchaseStep === 'quantity' ? (
          <section className="space-y-4" aria-labelledby="quantity-title">
            <div className="text-center">
              <h1 className="font-serif text-lg text-charcoal" id="quantity-title">
                Escolha seus números
              </h1>
            </div>

            <Card>
              <div className="flex items-center justify-center gap-8">
                <button
                  aria-label="Diminuir quantidade"
                  className="grid h-14 w-14 place-items-center rounded-full border-2 border-green text-green transition disabled:cursor-not-allowed disabled:opacity-30"
                  disabled={quantity === 1}
                  onClick={decreaseQuantity}
                  type="button"
                >
                  <Minus className="h-5 w-5" />
                </button>

                <div className="min-w-24 text-center">
                  <input
                    aria-label="Quantidade de números"
                    className="block w-28 appearance-none border-0 bg-transparent text-center font-serif text-7xl font-bold leading-none text-charcoal outline-none focus-visible:ring-2 focus-visible:ring-gold"
                    inputMode="numeric"
                    min="1"
                    onChange={(event) => changeQuantity(event.target.value)}
                    onFocus={(event) => event.currentTarget.select()}
                    pattern="[0-9]*"
                    step="1"
                    type="number"
                    value={quantity}
                  />
                  <span className="mt-1 block text-xs text-warm-gray">
                    {quantity === 1 ? 'número' : 'números'}
                  </span>
                </div>

                <button
                  aria-label="Aumentar quantidade"
                  className="grid h-14 w-14 place-items-center rounded-full bg-green text-white shadow-button transition hover:bg-green-deep"
                  onClick={increaseQuantity}
                  type="button"
                >
                  <Plus className="h-5 w-5" />
                </button>
              </div>
            </Card>

            {availableCombos.length ? (
              <section aria-labelledby="combo-title" className="space-y-3">
                <div className="text-center">
                  <h2 className="text-sm font-bold text-charcoal" id="combo-title">
                    Ou escolha um combo
                  </h2>
                  <p className="mt-1 text-xs text-warm-gray">Mais números com preço promocional</p>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  {availableCombos.map((combo) => (
                      <ComboCard
                        combo={combo}
                      isSelected={combo.id === selectedCombo?.id}
                      key={combo.id}
                      onSelect={() => selectCombo(combo)}
                    />
                  ))}
                </div>
              </section>
            ) : null}

            <Card className="bg-ivory-deep shadow-none">
              <dl className="space-y-3">
                <div className="flex items-center justify-between gap-4">
                  <dt className="text-sm text-warm-gray">Quantidade</dt>
                  <dd className="text-sm font-semibold">
                    {quantity} {quantity === 1 ? 'número' : 'números'}
                  </dd>
                </div>
                <div className="flex items-center justify-between gap-4">
                  <dt className="text-sm text-warm-gray">Valor unitário</dt>
                  <dd className="text-sm font-semibold">
                    {quoteQuery.isLoading
                      ? 'Atualizando...'
                      : unitPrice
                        ? formatCurrency(unitPrice)
                        : '-'}
                  </dd>
                </div>
                {selectedCombo ? (
                  <>
                    <div className="flex items-center justify-between gap-4">
                      <dt className="text-sm text-warm-gray">Valor normal</dt>
                      <dd className="text-sm font-semibold text-warm-gray line-through">
                        {formatCurrency(selectedCombo.regularPrice)}
                      </dd>
                    </div>
                    <div className="flex items-center justify-between gap-4">
                      <dt className="text-sm font-bold text-olive">Economia</dt>
                      <dd className="text-sm font-bold text-olive">
                        {formatCurrency(selectedCombo.savingsAmount)}
                      </dd>
                    </div>
                  </>
                ) : null}
                <div className="h-px bg-line" />
                <div className="flex items-center justify-between gap-4">
                  <dt className="text-base font-bold">Total</dt>
                  <dd className="font-serif text-3xl font-bold text-green">
                    {quoteQuery.isLoading
                      ? '...'
                      : totalAmount
                        ? formatCurrency(totalAmount)
                        : '-'}
                  </dd>
                </div>
              </dl>
            </Card>

            {quoteQuery.isError ? (
              <p
                className="rounded-lg border border-wine/30 bg-white px-4 py-3 text-sm text-wine"
                role="alert"
              >
                {publicMessages.quoteError}
              </p>
            ) : null}

            <Button
              disabled={!canContinueFromQuantity}
              onClick={() => setPurchaseStep('message')}
              type="button"
            >
              Continuar
            </Button>
            <Button onClick={() => setBuyer(null)} type="button" variant="secondary">
              Alterar dados
            </Button>
          </section>
        ) : purchaseStep === 'message' ? (
          <section className="space-y-4" aria-labelledby="message-title">
            <div className="text-center">
              <h1 className="font-serif text-lg text-charcoal" id="message-title">
                Mensagem para o casal
              </h1>
            </div>

            <Card className="bg-white/85 shadow-none">
              <label className="block text-sm font-semibold text-charcoal" htmlFor="gift-message">
                Mensagem para o casal (opcional)
              </label>
              <textarea
                className="mt-2 min-h-28 w-full resize-none rounded-lg border border-line bg-white px-4 py-3 text-base text-charcoal outline-none transition placeholder:text-warm-gray/60 focus:border-gold focus:ring-2 focus:ring-gold/20"
                id="gift-message"
                maxLength={GIFT_MESSAGE_MAX_LENGTH}
                onChange={(event) => setGiftMessage(event.target.value)}
                placeholder="Deixe uma mensagem de carinho"
                value={giftMessage}
              />
              <p className="mt-2 text-right text-xs font-semibold text-warm-gray">
                {giftMessage.length}/{GIFT_MESSAGE_MAX_LENGTH}
              </p>
            </Card>

            <div className="grid gap-3 sm:grid-cols-2">
              <Button onClick={() => setPurchaseStep('quantity')} type="button" variant="secondary">
                Voltar
              </Button>
              <Button
                disabled={giftMessageTooLong}
                onClick={() => setPurchaseStep('review')}
                type="button"
              >
                Revisar dados
              </Button>
            </div>
          </section>
        ) : (
          <section className="space-y-4" aria-labelledby="review-title">
            <div className="text-center">
              <h1 className="font-serif text-lg text-charcoal" id="review-title">
                Revise seus dados
              </h1>
            </div>

            <Card className="bg-ivory-deep shadow-none">
              <dl className="space-y-3">
                <div className="flex items-center justify-between gap-4">
                  <dt className="text-sm text-warm-gray">Nome</dt>
                  <dd className="text-right text-sm font-semibold">{buyer.name}</dd>
                </div>
                <div className="flex items-center justify-between gap-4">
                  <dt className="text-sm text-warm-gray">Telefone</dt>
                  <dd className="text-sm font-semibold">{formatPhoneNumber(buyer.phone)}</dd>
                </div>
                {buyer.email ? (
                  <div className="flex items-center justify-between gap-4">
                    <dt className="text-sm text-warm-gray">E-mail (opcional)</dt>
                    <dd className="text-right text-sm font-semibold">{buyer.email}</dd>
                  </div>
                ) : null}
                <div className="flex items-center justify-between gap-4">
                  <dt className="text-sm text-warm-gray">Quantidade</dt>
                  <dd className="text-sm font-semibold">
                    {quantity} {quantity === 1 ? 'número' : 'números'}
                  </dd>
                </div>
                <div className="h-px bg-line" />
                <div className="flex items-center justify-between gap-4">
                  <dt className="text-base font-bold">Total</dt>
                  <dd className="font-serif text-3xl font-bold text-green">
                    {quoteQuery.isLoading
                      ? '...'
                      : totalAmount
                        ? formatCurrency(totalAmount)
                        : '-'}
                  </dd>
                </div>
              </dl>
            </Card>

            {giftMessage.trim() ? (
              <Card className="bg-white/85 shadow-none">
                <p className="text-sm font-semibold text-charcoal">Mensagem</p>
                <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-warm-gray">
                  {giftMessage.trim()}
                </p>
              </Card>
            ) : null}

            {quoteQuery.isError ? (
              <p
                className="rounded-lg border border-wine/30 bg-white px-4 py-3 text-sm text-wine"
                role="alert"
              >
                {publicMessages.quoteError}
              </p>
            ) : null}

            {isCheckoutConfirmationOpen ? (
              <Card className="border border-gold bg-gold/10 text-left shadow-none">
                <h2 className="text-sm font-bold text-charcoal">Antes de ir para o pagamento</h2>
                <p className="mt-2 text-sm leading-relaxed text-warm-gray">
                  {publicMessages.checkoutRedirectNotice}
                </p>
                <div className="mt-4 grid gap-3 sm:grid-cols-2">
                  <Button
                    onClick={() => setIsCheckoutConfirmationOpen(false)}
                    type="button"
                    variant="secondary"
                  >
                    Cancelar
                  </Button>
                  <Button
                    disabled={isCheckoutBusy}
                    isLoading={isCheckoutBusy}
                    onClick={handlePay}
                    type="button"
                  >
                    Ir para o Mercado Pago
                  </Button>
                </div>
              </Card>
            ) : null}

            <div className="grid gap-3 sm:grid-cols-2">
              <Button
                onClick={() => {
                  setIsCheckoutConfirmationOpen(false);
                  setPurchaseStep('message');
                }}
                type="button"
                variant="secondary"
              >
                Voltar
              </Button>
              <Button
                disabled={
                  !canContinueFromQuantity ||
                  giftMessageTooLong ||
                  isCheckoutConfirmationOpen ||
                  isCheckoutBusy
                }
                isLoading={isCheckoutBusy}
                onClick={openCheckoutConfirmation}
                type="button"
              >
                <CreditCard aria-hidden="true" className="h-5 w-5" />
                Pagar com Mercado Pago
              </Button>
            </div>

            {createTransactionMutation.isError ? (
              <p
                className="rounded-lg border border-wine/30 bg-white px-4 py-3 text-sm text-wine"
                role="alert"
              >
                {publicMessages.checkoutError}
              </p>
            ) : null}

            <p className="px-2 text-center text-xs leading-relaxed text-warm-gray">
              Você será redirecionado ao Mercado Pago para concluir o pagamento com segurança.
            </p>
          </section>
        )}
        <RaffleResultPanel
          isDrawClosed={isDrawClosed}
          result={homeSummaryQuery.data?.raffleResult ?? null}
        />
        <FlagRankingPanel
          isLoading={homeSummaryQuery.isLoading}
          ranking={homeSummaryQuery.data?.flagRanking ?? []}
        />
      </div>
    </main>
  );
}

function CheckoutRedirectOverlay() {
  return (
    <div
      aria-busy="true"
      aria-live="polite"
      className="fixed inset-0 z-50 grid place-items-center bg-cream/95 px-6 backdrop-blur-sm"
      role="status"
    >
      <Card className="w-full max-w-sm border border-gold/40 bg-white text-center shadow-xl">
        <div className="mx-auto grid h-16 w-16 place-items-center rounded-full bg-gold/20 shadow-button">
          <div
            aria-label="Carregando redirecionamento"
            className="h-8 w-8 animate-spin rounded-full border-4 border-gold/30 border-t-gold"
            role="progressbar"
          />
        </div>
        <p className="mt-5 font-serif text-xl font-bold text-charcoal">
          Redirecionando para o Mercado Pago
        </p>
        <div className="mt-5 space-y-2">
          <div className="mx-auto h-3 w-4/5 animate-pulse rounded-full bg-line" />
          <div className="mx-auto h-3 w-2/3 animate-pulse rounded-full bg-line" />
        </div>
      </Card>
    </div>
  );
}

function multiplyMoney(amount: string | number, quantity: number): string {
  const [whole = '0', decimals = ''] = String(amount).split('.');
  const cents = Number(whole) * 100 + Number(decimals.padEnd(2, '0').slice(0, 2));
  return ((cents * quantity) / 100).toFixed(2);
}

interface PurchaseSubmission {
  idempotencyKey: string;
  request: BuyerData & { giftMessage?: string; quantity: number; comboId?: number };
}

function RecentCheckoutNotice({
  checkout,
  isError,
  isLoading,
  transaction,
}: {
  checkout: RecentCheckout;
  isError: boolean;
  isLoading: boolean;
  transaction?: TransactionStatusResponse;
}) {
  const statusHref = `/payment-return/pending?external_reference=${checkout.externalReference}`;
  const successHref = `/payment-return/success?external_reference=${checkout.externalReference}`;
  const checkoutUrl = transaction?.checkoutUrl ?? checkout.checkoutUrl;

  if (transaction?.status === 'APROVADO') {
    return (
      <Card className="border border-green/30 bg-white text-left shadow-none">
        <p className="text-sm font-bold text-green">Pagamento aprovado</p>
        <p className="mt-2 text-sm leading-relaxed text-warm-gray">
          Encontramos uma compra aprovada recentemente. Acesse a confirmação para ver seus números
          da sorte.
        </p>
        <a
          className="mt-4 inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-lg bg-green px-5 py-3 text-sm font-semibold text-white shadow-button transition hover:bg-green-deep focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green"
          href={successHref}
        >
          Ver meus números
        </a>
      </Card>
    );
  }

  return (
    <Card className="border border-gold bg-gold/10 text-left shadow-none">
      <p className="text-sm font-bold text-charcoal">
        {isLoading ? 'Consultando pagamento recente' : 'Pagamento em andamento'}
      </p>
      <p className="mt-2 text-sm leading-relaxed text-warm-gray">
        {isError
          ? 'Não conseguimos consultar sua compra recente agora. Você ainda pode abrir a tela de status.'
          : 'Estamos aguardando a confirmação do seu pagamento. Se você já pagou, toque em Ver status. Se ainda não concluiu, toque em Continuar pagamento.'}
      </p>
      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <a
          className="inline-flex min-h-12 w-full items-center justify-center rounded-lg bg-green px-5 py-3 text-sm font-semibold text-white shadow-button transition hover:bg-green-deep focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green"
          href={statusHref}
        >
          Ver status
        </a>
        <a
          className="inline-flex min-h-12 w-full items-center justify-center rounded-lg border border-green bg-transparent px-5 py-3 text-sm font-semibold text-green transition hover:bg-ivory-deep focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green"
          href={checkoutUrl}
        >
          Continuar pagamento
        </a>
      </div>
    </Card>
  );
}

function ComboCard({
  combo,
  isSelected,
  onSelect,
}: {
  combo: RaffleComboResponse;
  isSelected: boolean;
  onSelect: () => void;
}) {
  const badges = [
    combo.highlightMostChosen ? 'Mais escolhido' : null,
    combo.highlightBestValue ? 'Melhor valor' : null,
  ].filter((badge): badge is string => badge !== null);

  return (
    <button
      aria-pressed={isSelected}
      className={`relative min-h-28 rounded-xl border-2 px-3 pb-3 pt-4 text-left transition ${
        isSelected
          ? 'border-terracotta bg-blush shadow-button'
          : 'border-line bg-white hover:border-gold'
      }`}
      onClick={onSelect}
      type="button"
    >
      {badges.length ? (
        <span className="absolute -top-2 left-2 rounded-full bg-terracotta px-2 py-1 text-[10px] font-bold uppercase tracking-wide text-white">
          {badges.join(' · ')}
        </span>
      ) : null}
      <span className="block text-sm font-bold text-charcoal">{combo.quantity} números</span>
      <span className="mt-1 block font-serif text-xl font-bold text-green">
        {formatCurrency(combo.price)}
      </span>
      <span className="mt-1 block text-[11px] text-warm-gray line-through">
        Preço normal {formatCurrency(combo.regularPrice)}
      </span>
    </button>
  );
}

export function RaffleResultPanel({
  isDrawClosed,
  result,
}: {
  isDrawClosed: boolean;
  result: RaffleResult | null;
}) {
  if (!isDrawClosed || !result) return null;

  return (
    <aside>
      <Card className="border border-gold/40 bg-green-deep text-center text-white shadow-none">
        <p className="text-xs font-bold uppercase tracking-wide text-gold">Número ganhador</p>
        <p className="mt-3 font-serif text-6xl font-bold leading-none text-gold">
          {result.winningNumber}
        </p>
        <p className="mt-4 font-serif text-2xl font-bold text-green">{result.winnerName}</p>
        {result.participantFlagEmoji && result.participantFlagName ? (
          <div className="mt-4 inline-flex items-center gap-3 rounded-lg bg-ivory px-4 py-3 shadow-sm">
            <span className="grid h-11 w-11 place-items-center rounded-full bg-white">
              <FlagEmoji className="h-7 w-7" emoji={result.participantFlagEmoji} />
            </span>
            <span className="text-sm font-bold text-green-deep">{result.participantFlagName}</span>
          </div>
        ) : null}
      </Card>
    </aside>
  );
}
