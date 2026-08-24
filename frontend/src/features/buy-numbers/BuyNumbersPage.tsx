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
import { isPastDateTime } from '../../utils/dateTime';
import { formatCurrency } from '../../utils/formatters';
import { clearIdempotencyKey, getOrCreateIdempotencyKey } from '../../utils/idempotency';
import { formatPhoneNumber, normalizePhoneNumber } from '../../utils/phone';
import { FlagRankingPanel } from '../flag-ranking/FlagRankingPanel';
import { CountdownPanel } from './CountdownPanel';
import { buyerSchema, type BuyerFormData } from './schemas';

const CHECKOUT_IDEMPOTENCY_ACTION = 'mercado-pago-checkout';

export function BuyNumbersPage({ showBackLink = false }: { showBackLink?: boolean }) {
  const [buyer, setBuyer] = useState<BuyerFormData | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [, setTick] = useState(0);

  const {
    formState: { errors, isValid },
    handleSubmit,
    register,
  } = useForm<BuyerFormData>({
    defaultValues: { name: '', phone: '' },
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
    queryKey: ['transaction-quote', buyer, quantity],
    queryFn: () => transactionService.quote({ ...buyer!, quantity }),
  });

  useEffect(() => {
    if (!scheduledDrawAt) return undefined;

    const intervalId = window.setInterval(() => setTick((current) => current + 1), 1000);
    return () => window.clearInterval(intervalId);
  }, [scheduledDrawAt]);

  const createTransactionMutation = useMutation({
    mutationFn: ({ idempotencyKey, request }: PurchaseSubmission) =>
      transactionService.create(request, idempotencyKey),
    onSuccess: (response, submission) => {
      clearIdempotencyKey(CHECKOUT_IDEMPOTENCY_ACTION, submission.idempotencyKey);
      window.location.assign(response.checkoutUrl);
    },
  });

  const onSubmitBuyer = (data: BuyerFormData) => {
    setBuyer({
      name: data.name.trim(),
      phone: normalizePhoneNumber(data.phone),
    });
  };

  const decreaseQuantity = () => setQuantity((current) => Math.max(1, current - 1));
  const increaseQuantity = () => setQuantity((current) => current + 1);

  const handlePay = () => {
    if (!buyer || isDrawClosed || createTransactionMutation.isPending) return;
    const request = { ...buyer, quantity };
    createTransactionMutation.mutate({
      idempotencyKey: getOrCreateIdempotencyKey(CHECKOUT_IDEMPOTENCY_ACTION, request),
      request,
    });
  };

  const currentStep: 1 | 2 = buyer ? 2 : 1;
  const unitPrice = quoteQuery.data?.unitPrice;
  const totalAmount = quoteQuery.data?.totalAmount;

  return (
    <main className="min-h-screen bg-cream px-6 pb-16 pt-10 text-charcoal">
      <div className="mx-auto flex w-full max-w-[480px] flex-col gap-7">
        <header className="text-center">
          <BrandMark />
          <p className="mx-auto mt-4 max-w-xs text-sm leading-relaxed text-warm-gray">
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

        <CountdownPanel scheduledDrawAt={scheduledDrawAt} />

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
        ) : (
          <section className="space-y-4" aria-labelledby="quantity-title">
            <div className="text-center">
              <h1 className="font-serif text-lg text-charcoal" id="quantity-title">
                Quantos números você quer?
              </h1>
              <button
                className="mt-2 text-xs font-semibold text-green underline underline-offset-4"
                onClick={() => setBuyer(null)}
                type="button"
              >
                Alterar dados
              </button>
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
                  <span className="block font-serif text-7xl font-bold leading-none text-charcoal">
                    {quantity}
                  </span>
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
                <div className="h-px bg-line" />
                <div className="flex items-center justify-between gap-4">
                  <dt className="text-base font-bold">Total</dt>
                  <dd className="font-serif text-3xl font-bold text-green">
                    {quoteQuery.isLoading ? '...' : totalAmount ? formatCurrency(totalAmount) : '-'}
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

            {createTransactionMutation.isError ? (
              <p
                className="rounded-lg border border-wine/30 bg-white px-4 py-3 text-sm text-wine"
                role="alert"
              >
                {publicMessages.checkoutError}
              </p>
            ) : null}

            <Button
              disabled={!quoteQuery.data || quoteQuery.isFetching}
              isLoading={createTransactionMutation.isPending}
              onClick={handlePay}
              type="button"
            >
              <CreditCard aria-hidden="true" className="h-5 w-5" />
              Pagar com Mercado Pago
            </Button>

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

interface PurchaseSubmission {
  idempotencyKey: string;
  request: BuyerFormData & { quantity: number };
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
