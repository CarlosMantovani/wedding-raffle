import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { ArrowLeft, ChevronDown, Download, ReceiptText } from 'lucide-react';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';

import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { FlagEmoji } from '../../components/ui/FlagEmoji';
import { TextInput } from '../../components/ui/TextInput';
import { adminTransactionService } from '../../services/adminTransactionService';
import { transactionService } from '../../services/transactionService';
import { formatCurrency } from '../../utils/formatters';
import { clearIdempotencyKey, getOrCreateIdempotencyKey } from '../../utils/idempotency';
import { formatPhoneNumber, normalizePhoneNumber } from '../../utils/phone';
import { RecoveryCodeContent } from '../payment-return/PaymentReturnPage';
import { cashPaymentSchema, type CashPaymentFormData } from './schemas';

const CASH_IDEMPOTENCY_ACTION = 'cash-registration';

export function AdminCashPaymentPage() {
  const [isCurrentLuckyNumberListExpanded, setIsCurrentLuckyNumberListExpanded] = useState(false);
  const [isPreviousLuckyNumberListExpanded, setIsPreviousLuckyNumberListExpanded] = useState(false);
  const {
    control,
    formState: { errors, isValid },
    handleSubmit,
    register,
  } = useForm<CashPaymentFormData>({
    defaultValues: { name: '', phone: '', quantity: 1 },
    mode: 'onChange',
    resolver: zodResolver(cashPaymentSchema),
  });

  const createCashMutation = useMutation({
    mutationFn: ({ idempotencyKey, request }: CashPurchaseSubmission) =>
      adminTransactionService.createCashTransaction(request, idempotencyKey),
    onSuccess: (_response, submission) => {
      clearIdempotencyKey(CASH_IDEMPOTENCY_ACTION, submission.idempotencyKey);
      setIsCurrentLuckyNumberListExpanded(false);
      setIsPreviousLuckyNumberListExpanded(false);
    },
  });

  const submitCashPayment = (data: CashPaymentFormData) => {
    const request = {
      name: data.name.trim(),
      phone: normalizePhoneNumber(data.phone),
      quantity: data.quantity,
    };
    createCashMutation.mutate({
      idempotencyKey: getOrCreateIdempotencyKey(CASH_IDEMPOTENCY_ACTION, request),
      request,
    });
  };
  const createdLuckyNumbers = createCashMutation.data?.luckyNumbers ?? [];
  const previousLuckyNumbers = createCashMutation.data?.previousLuckyNumbers ?? [];
  const totalLuckyNumbers =
    createCashMutation.data?.totalLuckyNumbers ?? createdLuckyNumbers.length;

  return (
    <main className="min-h-screen bg-cream text-charcoal">
      <header className="bg-charcoal px-6 py-4 text-white">
        <div className="mx-auto flex max-w-4xl items-center justify-between gap-4">
          <div>
            <p className="font-serif text-2xl font-bold">
              Presente <span className="italic text-gold">Premiado</span>
            </p>
            <p className="mt-1 text-xs text-white/55">Pagamento em dinheiro</p>
          </div>
          <a
            className="inline-flex items-center gap-2 text-sm font-semibold text-white/70 hover:text-white"
            href="/admin"
          >
            <ArrowLeft aria-hidden="true" className="h-4 w-4" />
            Voltar
          </a>
        </div>
      </header>

      <section className="mx-auto grid max-w-4xl gap-6 px-6 py-8 md:grid-cols-[1fr_0.9fr]">
        <Card>
          <form className="space-y-5" onSubmit={handleSubmit(submitCashPayment)}>
            <div>
              <h1 className="font-serif text-2xl font-bold">Registrar pagamento</h1>
              <p className="mt-1 text-sm text-warm-gray">
                Os números são gerados imediatamente após confirmar.
              </p>
            </div>

            <TextInput
              id="cash-name"
              label="Nome"
              placeholder="Nome do convidado"
              error={errors.name?.message}
              {...register('name')}
            />

            <Controller
              control={control}
              name="phone"
              render={({ field }) => (
                <TextInput
                  id="cash-phone"
                  label="Telefone"
                  placeholder="(11) 99999-9999"
                  maxLength={15}
                  inputMode="tel"
                  type="tel"
                  error={errors.phone?.message}
                  {...field}
                  onChange={(event) => field.onChange(formatPhoneNumber(event.target.value))}
                />
              )}
            />

            <TextInput
              id="cash-quantity"
              label="Quantidade"
              min={1}
              type="number"
              error={errors.quantity?.message}
              {...register('quantity')}
            />

            {createCashMutation.isError ? (
              <p
                className="rounded-lg border border-terracotta/30 bg-blush px-4 py-3 text-sm text-terracotta-dark"
                role="alert"
              >
                Não foi possível registrar o pagamento.
              </p>
            ) : null}

            <Button disabled={!isValid} isLoading={createCashMutation.isPending} type="submit">
              <ReceiptText aria-hidden="true" className="h-5 w-5" />
              Confirmar pagamento
            </Button>
          </form>
        </Card>

        <Card className="bg-blush shadow-none">
          {createCashMutation.data ? (
            <div className="space-y-5">
              <div>
                <p className="text-xs font-bold uppercase tracking-wide text-warm-gray">
                  Pagamento aprovado
                </p>
                <h2 className="mt-2 font-serif text-2xl font-bold">
                  {createCashMutation.data.name}
                </h2>
                <p className="mt-1 text-sm text-warm-gray">
                  {formatCurrency(createCashMutation.data.totalAmount)}
                </p>
                {createCashMutation.data.participantFlagEmoji &&
                createCashMutation.data.participantFlagName ? (
                  <div className="mt-4 rounded-lg border border-[#EEE6DF] bg-white/60 px-4 py-3">
                    <p className="text-xs font-bold uppercase tracking-wide text-terracotta">
                      Bandeira do participante
                    </p>
                    <div className="mt-3 flex items-center gap-3">
                      <span className="grid h-12 w-12 place-items-center rounded-full bg-blush">
                        <FlagEmoji
                          className="h-8 w-8"
                          emoji={createCashMutation.data.participantFlagEmoji}
                        />
                      </span>
                      <span className="font-serif text-xl font-bold text-charcoal">
                        {createCashMutation.data.participantFlagName}
                      </span>
                    </div>
                  </div>
                ) : null}
              </div>

              {previousLuckyNumbers.length > 0 ? (
                <dl className="grid gap-2 rounded-lg border border-gold/30 bg-white/60 px-4 py-3 text-sm">
                  <div className="flex items-center justify-between gap-3">
                    <dt className="text-warm-gray">Números adquiridos anteriormente:</dt>
                    <dd className="font-bold text-warm-gray">{previousLuckyNumbers.length}</dd>
                  </div>
                  <div className="flex items-center justify-between gap-3">
                    <dt className="text-warm-gray">Números adquiridos agora:</dt>
                    <dd className="font-bold text-terracotta">{createdLuckyNumbers.length}</dd>
                  </div>
                  <div className="flex items-center justify-between gap-3">
                    <dt className="text-warm-gray">Total de números com esta compra:</dt>
                    <dd className="font-bold text-green">{totalLuckyNumbers}</dd>
                  </div>
                </dl>
              ) : null}

              <div className="rounded-lg border border-green/20 bg-white/60 p-4">
                <RecoveryCodeContent recoveryCode={createCashMutation.data.recoveryCode} />
              </div>

              <CashLuckyNumberGroup
                isExpanded={isCurrentLuckyNumberListExpanded}
                numbers={createdLuckyNumbers}
                onToggle={() => setIsCurrentLuckyNumberListExpanded((current) => !current)}
                title={
                  previousLuckyNumbers.length > 0 ? 'Números adquiridos agora' : 'Números gerados'
                }
              />

              {previousLuckyNumbers.length > 0 ? (
                <CashLuckyNumberGroup
                  isExpanded={isPreviousLuckyNumberListExpanded}
                  numbers={previousLuckyNumbers}
                  onToggle={() => setIsPreviousLuckyNumberListExpanded((current) => !current)}
                  title="Números adquiridos anteriormente"
                />
              ) : null}

              <a
                className="inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-lg bg-terracotta px-4 py-2 text-sm font-semibold text-white shadow-button transition hover:bg-terracotta-dark"
                href={transactionService.getLuckyNumbersPdfUrl(
                  createCashMutation.data.externalReference,
                )}
              >
                <Download aria-hidden="true" className="h-4 w-4" />
                Baixar PDF
              </a>
            </div>
          ) : (
            <div className="grid min-h-72 place-items-center text-center">
              <div>
                <ReceiptText aria-hidden="true" className="mx-auto h-12 w-12 text-terracotta" />
                <p className="mt-4 text-sm leading-relaxed text-warm-gray">
                  Depois de confirmar, os números aparecerão aqui para entrega ao convidado.
                </p>
              </div>
            </div>
          )}
        </Card>
      </section>
    </main>
  );
}

interface CashPurchaseSubmission {
  idempotencyKey: string;
  request: {
    name: string;
    phone: string;
    quantity: number;
  };
}

function CashLuckyNumberGroup({
  isExpanded,
  numbers,
  onToggle,
  title,
}: {
  isExpanded: boolean;
  numbers: string[];
  onToggle: () => void;
  title: string;
}) {
  const hasMoreLuckyNumbers = numbers.length > 8;
  const displayedLuckyNumbers = isExpanded ? numbers : numbers.slice(0, 8);

  return (
    <section aria-label={title} className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-sm font-bold text-charcoal">{title}</h3>
        <span className="shrink-0 rounded-full bg-white/70 px-3 py-1 text-xs font-bold text-warm-gray">
          {numbers.length}
        </span>
      </div>
      <button
        aria-expanded={hasMoreLuckyNumbers ? isExpanded : undefined}
        className={`w-full rounded-lg border border-gold/40 bg-white/55 p-3 text-left ${
          hasMoreLuckyNumbers ? 'cursor-pointer transition hover:bg-white/80' : 'cursor-default'
        }`}
        disabled={!hasMoreLuckyNumbers}
        onClick={onToggle}
        type="button"
      >
        <div className="grid w-full grid-cols-4 gap-2">
          {displayedLuckyNumbers.map((number) => (
            <span
              className="flex min-w-0 items-center justify-center whitespace-nowrap rounded-md bg-gold px-2 py-1.5 font-mono text-xs font-bold tabular-nums text-charcoal"
              key={number}
              title={number}
            >
              {number}
            </span>
          ))}
        </div>
        {hasMoreLuckyNumbers ? (
          <ChevronDown
            aria-hidden="true"
            className={`mx-auto mt-3 h-4 w-4 text-terracotta transition-transform ${isExpanded ? 'rotate-180' : ''}`}
          />
        ) : null}
      </button>
    </section>
  );
}
