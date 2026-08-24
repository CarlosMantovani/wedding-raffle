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
import type { RaffleComboResponse } from '../../types/transaction';
import { isPastDateTime } from '../../utils/dateTime';
import { formatCurrency } from '../../utils/formatters';
import { clearIdempotencyKey, getOrCreateIdempotencyKey } from '../../utils/idempotency';
import { formatPhoneNumber, normalizePhoneNumber } from '../../utils/phone';
import { buyerSchema, type BuyerFormData } from './schemas';

const CHECKOUT_IDEMPOTENCY_ACTION = 'mercado-pago-checkout';

export function BuyNumbersPage({ showBackLink = false }: { showBackLink?: boolean }) {
  const [buyer, setBuyer] = useState<BuyerFormData | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [selectedComboId, setSelectedComboId] = useState<number | null>(null);
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
    placeholderData: (previousData) => previousData,
    queryKey: ['transaction-quote', buyer, quantity, selectedComboId],
    queryFn: () =>
      transactionService.quote({
        ...buyer!,
        quantity,
        ...(selectedComboId === null ? {} : { comboId: selectedComboId }),
      }),
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
    if (!buyer || isDrawClosed || createTransactionMutation.isPending) return;
    const request = {
      ...buyer,
      quantity,
      ...(selectedComboId === null ? {} : { comboId: selectedComboId }),
    };
    createTransactionMutation.mutate({
      idempotencyKey: getOrCreateIdempotencyKey(CHECKOUT_IDEMPOTENCY_ACTION, request),
      request,
    });
  };

  const currentStep: 1 | 2 = buyer ? 2 : 1;
  const unitPrice = quoteQuery.data?.unitPrice;
  const totalAmount = quoteQuery.data?.totalAmount;
  const quoteMatchesSelection =
    quoteQuery.data?.quantity === quantity &&
    (quoteQuery.data?.comboId ?? null) === selectedComboId;
  const selectedCombo = quoteMatchesSelection
    ? quoteQuery.data?.availableCombos.find((combo) => combo.id === selectedComboId)
    : undefined;

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

            {quoteQuery.data?.availableCombos.length ? (
              <section aria-labelledby="combo-title" className="space-y-3">
                <div className="text-center">
                  <h2 className="text-sm font-bold text-charcoal" id="combo-title">
                    Ou escolha um combo
                  </h2>
                  <p className="mt-1 text-xs text-warm-gray">Mais números com preço promocional</p>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  {quoteQuery.data.availableCombos.map((combo) => (
                    <ComboCard
                      combo={combo}
                      isSelected={combo.id === selectedComboId}
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
                    {quoteQuery.isFetching || !quoteMatchesSelection
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
                    {quoteQuery.isFetching || !quoteMatchesSelection
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

            {createTransactionMutation.isError ? (
              <p
                className="rounded-lg border border-wine/30 bg-white px-4 py-3 text-sm text-wine"
                role="alert"
              >
                {publicMessages.checkoutError}
              </p>
            ) : null}

            <Button
              disabled={!quoteQuery.data || quoteQuery.isFetching || !quoteMatchesSelection}
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
      </div>
    </main>
  );
}

interface PurchaseSubmission {
  idempotencyKey: string;
  request: BuyerFormData & { quantity: number; comboId?: number };
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
      className={`relative min-h-36 rounded-xl border-2 px-3 pb-3 pt-5 text-left transition ${
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
      <span className="mt-2 block text-xs font-semibold text-olive">
        Economize {formatCurrency(combo.savingsAmount)}
      </span>
      <span className="mt-1 block text-[11px] text-warm-gray">
        {formatCurrency(combo.averagePricePerNumber)} por número
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
