import { useQuery } from '@tanstack/react-query';
import { AlertTriangle, Check, Copy, Download, Gift, Loader2 } from 'lucide-react';
import type { ReactNode } from 'react';
import { useState } from 'react';

import { BrandMark, GoldDivider } from '../../components/brand/BrandMark';
import { Card } from '../../components/ui/Card';
import { FlagEmoji } from '../../components/ui/FlagEmoji';
import { publicMessages } from '../../content/messages';
import { transactionService } from '../../services/transactionService';

function getExternalReference(searchParams: URLSearchParams) {
  return searchParams.get('external_reference') ?? searchParams.get('externalReference') ?? '';
}

export function PaymentReturnPage() {
  const searchParams = new URLSearchParams(window.location.search);
  const externalReference = getExternalReference(searchParams);

  const statusQuery = useQuery({
    enabled: Boolean(externalReference),
    queryKey: ['transaction-status', externalReference],
    queryFn: () => transactionService.getStatus(externalReference),
    refetchInterval: (query) => (query.state.data?.status === 'PENDENTE' ? 5000 : false),
  });

  if (!externalReference) {
    return <PaymentState title="Não foi possível localizar sua compra" message={publicMessages.missingReference} tone="error" />;
  }

  if (statusQuery.isLoading) {
    return (
      <PaymentState
        icon={<Loader2 aria-hidden="true" className="h-10 w-10 animate-spin text-terracotta" />}
        message="Estamos confirmando o status real do seu pagamento."
        title="Consultando pagamento"
        tone="neutral"
      />
    );
  }

  if (statusQuery.isError || !statusQuery.data) {
    return <PaymentState title="Não foi possível confirmar o pagamento" message={publicMessages.genericError} tone="error" />;
  }

  const transaction = statusQuery.data;
  const previousLuckyNumbers = transaction.previousLuckyNumbers ?? [];
  const totalLuckyNumbers = transaction.totalLuckyNumbers ?? transaction.luckyNumbers.length;

  if (transaction.status === 'APROVADO' && transaction.luckyNumbers.length > 0) {
    return (
      <main className="min-h-screen bg-cream px-6 pb-16 pt-10 text-charcoal">
        <div className="mx-auto flex w-full max-w-[440px] flex-col gap-6 text-center">
          <div>
            <Gift aria-hidden="true" className="mx-auto mb-5 h-14 w-14 text-terracotta" />
            <h1 className="font-serif text-4xl font-bold leading-tight">
              Muito obrigado pela <span className="italic text-terracotta">sua gentileza!</span>
            </h1>
            <p className="mx-auto mt-4 max-w-xs text-sm leading-relaxed text-warm-gray">
              Sua participação foi confirmada. Boa sorte no sorteio!
            </p>
            <div className="mt-6">
              <GoldDivider />
            </div>
          </div>

          <Card className="border border-[#EEE6DF] bg-white/90 text-center shadow-none">
            <p className="text-xs font-bold uppercase tracking-wide text-terracotta">Sua bandeira</p>
            <div className="mt-3 flex items-center justify-center gap-3">
              <span className="grid h-14 w-14 place-items-center rounded-full bg-blush">
                <FlagEmoji className="h-9 w-9" emoji={transaction.participantFlagEmoji} />
              </span>
              <span className="font-serif text-2xl font-bold text-charcoal">{transaction.participantFlagName}</span>
            </div>
          </Card>

          <Card className="border border-gold/30 text-center">
            {previousLuckyNumbers.length > 0 ? (
              <div className="space-y-5 text-left">
                <div className="text-center">
                  <h2 className="font-serif text-lg font-semibold">Resumo dos seus números</h2>
                  <dl className="mt-4 grid gap-2 rounded-lg bg-cream px-4 py-3 text-sm">
                    <div className="flex items-center justify-between gap-3">
                      <dt className="text-warm-gray">Números adquiridos anteriormente:</dt>
                      <dd className="font-bold text-warm-gray">{previousLuckyNumbers.length}</dd>
                    </div>
                    <div className="flex items-center justify-between gap-3">
                      <dt className="text-warm-gray">Números adquiridos agora:</dt>
                      <dd className="font-bold text-terracotta">{transaction.luckyNumbers.length}</dd>
                    </div>
                    <div className="flex items-center justify-between gap-3">
                      <dt className="text-warm-gray">Total de números com esta compra:</dt>
                      <dd className="font-bold text-green">{totalLuckyNumbers}</dd>
                    </div>
                  </dl>
                </div>
                <LuckyNumberGroup title="Números adquiridos agora" numbers={transaction.luckyNumbers} tone="highlight" />
                <LuckyNumberGroup title="Números adquiridos anteriormente" numbers={previousLuckyNumbers} tone="muted" />
              </div>
            ) : (
              <>
                <h2 className="font-serif text-lg font-semibold">Seus números da sorte</h2>
                <LuckyNumberList className="mt-5" numbers={transaction.luckyNumbers} tone="highlight" />
              </>
            )}
          </Card>

          <RecoveryCodeCard recoveryCode={transaction.recoveryCode} />

          <PdfDownloadCard externalReference={transaction.externalReference} />

          <p className="font-serif text-sm italic leading-relaxed text-terracotta">
            Que este número te traga a alegria de celebrar junto ao casal neste dia tão especial.
          </p>

          <a className="text-sm font-semibold text-warm-gray underline underline-offset-4" href="/">
            Voltar ao início
          </a>
        </div>
      </main>
    );
  }

  if (transaction.status === 'PENDENTE') {
    return (
      <PaymentState
        recoveryCode={transaction.recoveryCode}
        title="Pagamento pendente"
        message={publicMessages.pending}
        tone="pending"
      />
    );
  }

  if (transaction.status === 'APROVADO') {
    return (
      <PaymentState
        icon={<Check aria-hidden="true" className="h-10 w-10 text-green" />}
        title="Pagamento confirmado"
        message={publicMessages.approvedWithoutNumbers}
        tone="confirmed"
      />
    );
  }

  if (transaction.status === 'CANCELADO') {
    return <PaymentState title="Pagamento cancelado" message={publicMessages.cancelled} tone="error" />;
  }

  return <PaymentState title="Pagamento recusado" message={publicMessages.rejected} tone="error" />;
}

export function RecoveryCodeCard({ recoveryCode }: { recoveryCode: string }) {
  return (
    <Card className="border border-green/30 bg-white text-left shadow-none">
      <RecoveryCodeContent recoveryCode={recoveryCode} />
    </Card>
  );
}

export function RecoveryCodeContent({ recoveryCode }: { recoveryCode: string }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    await navigator.clipboard?.writeText(recoveryCode);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1800);
  };

  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="space-y-2">
        <h2 className="text-sm font-bold">Código de consulta</h2>
        <p className="mt-1 text-sm leading-relaxed text-warm-gray">
          Este código é único para todas as suas compras e não muda. Guarde para consultar seus números depois pelo telefone
          na tela inicial.
        </p>
        <p className="text-sm leading-relaxed text-warm-gray">
          Não compartilhe com ninguém: quem tiver esse código junto com seu telefone também poderá consultar seus números.
        </p>
      </div>
      <div className="flex shrink-0 items-center gap-2 self-start sm:self-auto">
        <span className="rounded-lg bg-ivory-deep px-4 py-2 font-serif text-2xl font-bold tracking-normal text-green">
          {recoveryCode}
        </span>
        <button
          aria-label={copied ? 'Código copiado' : 'Copiar código'}
          className="grid h-11 w-11 place-items-center rounded-lg bg-green text-white transition hover:bg-green-deep"
          onClick={handleCopy}
          type="button"
        >
          {copied ? <Check aria-hidden="true" className="h-5 w-5" /> : <Copy aria-hidden="true" className="h-5 w-5" />}
        </button>
      </div>
    </div>
  );
}

function LuckyNumberGroup({
  numbers,
  title,
  tone,
}: {
  numbers: string[];
  title: string;
  tone: 'highlight' | 'muted';
}) {
  return (
    <section aria-label={title}>
      <div className="mb-3 flex items-center justify-between gap-3">
        <h3 className="text-sm font-bold text-charcoal">{title}</h3>
        <span className="shrink-0 rounded-full bg-cream px-3 py-1 text-xs font-bold text-warm-gray">{numbers.length}</span>
      </div>
      <LuckyNumberList numbers={numbers} tone={tone} />
    </section>
  );
}

function LuckyNumberList({
  className = '',
  numbers,
  tone,
}: {
  className?: string;
  numbers: string[];
  tone: 'highlight' | 'muted';
}) {
  const toneClass = tone === 'highlight' ? 'bg-gold text-charcoal' : 'bg-cream text-warm-gray';

  return (
    <div className={`flex flex-wrap justify-center gap-3 ${className}`}>
      {numbers.map((number) => (
        <span className={`rounded-lg px-4 py-2 text-sm font-bold shadow-sm ${toneClass}`} key={number}>
          {number}
        </span>
      ))}
    </div>
  );
}

function PdfDownloadCard({ externalReference }: { externalReference: string }) {
  return (
    <Card className="border border-gold bg-gold/10 text-left shadow-none">
      <PdfDownloadContent externalReference={externalReference} />
    </Card>
  );
}

export function PdfDownloadContent({ externalReference }: { externalReference: string }) {
  return (
    <div className="flex gap-4 text-left">
      <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-gold text-charcoal">
        <Download aria-hidden="true" className="h-5 w-5" />
      </span>
      <div className="flex-1">
        <h2 className="text-sm font-bold">Baixe seus números agora</h2>
        <p className="mt-1 text-sm leading-relaxed text-warm-gray">
          Baixe o PDF para guardar seus números junto com o código de consulta.
        </p>
        <a
          className="mt-4 inline-flex min-h-11 items-center justify-center gap-2 rounded-lg bg-terracotta px-4 py-2 text-sm font-semibold text-white shadow-button transition hover:bg-terracotta-dark"
          href={transactionService.getLuckyNumbersPdfUrl(externalReference)}
        >
          <Download aria-hidden="true" className="h-4 w-4" />
          Baixar PDF
        </a>
      </div>
    </div>
  );
}

interface PaymentStateProps {
  icon?: ReactNode;
  message: string;
  recoveryCode?: string;
  title: string;
  tone: 'confirmed' | 'error' | 'neutral' | 'pending';
}

function PaymentState({ icon, message, recoveryCode, title, tone }: PaymentStateProps) {
  const iconColor = tone === 'pending' ? 'text-gold' : tone === 'confirmed' ? 'text-green' : tone === 'neutral' ? 'text-terracotta' : 'text-terracotta-dark';

  return (
    <main className="min-h-screen bg-cream px-6 pb-16 pt-16 text-charcoal">
      <div className="mx-auto flex w-full max-w-[420px] flex-col items-center gap-7 text-center">
        <BrandMark />
        <div className="grid h-20 w-20 place-items-center rounded-full bg-blush">
          {icon ?? <AlertTriangle aria-hidden="true" className={`h-10 w-10 ${iconColor}`} />}
        </div>
        <div>
          <h1 className="font-serif text-3xl font-bold">{title}</h1>
          <p className="mx-auto mt-4 max-w-xs text-sm leading-relaxed text-warm-gray">{message}</p>
        </div>
        {recoveryCode ? <RecoveryCodeCard recoveryCode={recoveryCode} /> : null}
        <div className="flex w-full flex-col gap-3">
          {tone === 'error' ? (
            <a
              className="inline-flex min-h-12 w-full items-center justify-center rounded-lg bg-terracotta px-5 py-3 text-sm font-semibold text-white shadow-button transition hover:bg-terracotta-dark focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-terracotta"
              href="/"
            >
              Tentar novamente
            </a>
          ) : null}
          <a
            className="inline-flex min-h-12 w-full items-center justify-center rounded-lg border border-terracotta bg-transparent px-5 py-3 text-sm font-semibold text-terracotta transition hover:bg-blush focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-terracotta"
            href="/"
          >
            Voltar ao início
          </a>
        </div>
      </div>
    </main>
  );
}
