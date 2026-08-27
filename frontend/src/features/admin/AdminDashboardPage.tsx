import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronDown, Download, Eye, EyeOff, Gift, LogOut, Menu, MessageSquareText, ReceiptText, Settings, Search, Trash2, X } from 'lucide-react';
import { useState } from 'react';

import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { TextInput } from '../../components/ui/TextInput';
import { adminTransactionService } from '../../services/adminTransactionService';
import type { AdminTransactionResponse, AdminTransactionSummaryResponse, CapacityReviewDecision } from '../../types/admin';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import { formatPhoneNumber } from '../../utils/phone';
import { useAuth } from './AuthContext';

const PAGE_SIZE = 20;
const EMPTY_TRANSACTIONS: AdminTransactionResponse[] = [];
const SORT_OPTIONS = [
  { label: 'Data: mais recente', value: 'createdAt,desc' },
  { label: 'Data: mais antiga', value: 'createdAt,asc' },
  { label: 'Valor: maior primeiro', value: 'totalAmount,desc' },
  { label: 'Valor: menor primeiro', value: 'totalAmount,asc' },
  { label: 'Status', value: 'status,asc' },
  { label: 'Nome: A-Z', value: 'name,asc' },
  { label: 'Nome: Z-A', value: 'name,desc' },
] as const;

type AdminTransactionSort = (typeof SORT_OPTIONS)[number]['value'];

export function AdminDashboardPage() {
  const { isMaster, logout } = useAuth();
  const queryClient = useQueryClient();
  const [queryFilter, setQueryFilter] = useState('');
  const [submittedQueryFilter, setSubmittedQueryFilter] = useState('');
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<AdminTransactionSort>('createdAt,desc');
  const [areMetricsVisible, setAreMetricsVisible] = useState(false);
  const [isAdminMenuOpen, setIsAdminMenuOpen] = useState(false);

  const transactionsQuery = useQuery({
    queryKey: ['admin-transactions', submittedQueryFilter, page, sort],
    queryFn: () => adminTransactionService.list({ query: submittedQueryFilter, page, size: PAGE_SIZE, sort }),
  });
  const summaryQuery = useQuery({
    queryKey: ['admin-transaction-summary'],
    queryFn: () => adminTransactionService.getSummary(),
    enabled: isMaster,
  });
  const deleteCashTransactionMutation = useMutation({
    mutationFn: (externalReference: string) =>
      adminTransactionService.deleteCashTransaction(externalReference),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin-transactions'] });
      void queryClient.invalidateQueries({ queryKey: ['admin-transaction-summary'] });
    },
  });
  const participantPdfMutation = useMutation({
    mutationFn: (transaction: AdminTransactionResponse) =>
      adminTransactionService.getParticipantLuckyNumbersPdf(transaction.externalReference),
    onSuccess: (pdf, transaction) => {
      downloadPdf(pdf, `Numeros_do_participante_${shortReference(transaction.externalReference)}.pdf`);
    },
  });
  const capacityReviewMutation = useMutation({
    mutationFn: ({ decision, transaction }: { decision: CapacityReviewDecision; transaction: AdminTransactionResponse }) =>
      adminTransactionService.resolveCapacityReview(transaction.externalReference, decision),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin-transactions'] });
      void queryClient.invalidateQueries({ queryKey: ['admin-transaction-summary'] });
    },
  });

  const transactions = transactionsQuery.data?.content ?? EMPTY_TRANSACTIONS;
  const submitFilter = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setPage(0);
    setSubmittedQueryFilter(queryFilter.trim());
  };

  return (
    <main className="min-h-screen bg-cream text-charcoal">
      <header className="bg-charcoal px-6 py-4 text-white">
        <div className="mx-auto flex max-w-6xl flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="font-serif text-2xl font-bold">
                Presente <span className="italic text-gold">Premiado</span>
              </p>
              <p className="mt-1 text-xs text-white/55">Painel administrativo</p>
            </div>
            <button
              aria-expanded={isAdminMenuOpen}
              aria-label={isAdminMenuOpen ? 'Fechar menu administrativo' : 'Abrir menu administrativo'}
              className="inline-flex h-11 w-11 items-center justify-center rounded-lg border border-white/20 text-white transition hover:bg-white/10 focus-visible:outline focus-visible:outline-2 focus-visible:outline-gold md:hidden"
              onClick={() => setIsAdminMenuOpen((current) => !current)}
              type="button"
            >
              {isAdminMenuOpen ? <X aria-hidden="true" className="h-5 w-5" /> : <Menu aria-hidden="true" className="h-5 w-5" />}
            </button>
          </div>
          <nav
            aria-label="Menu administrativo"
            className={`${isAdminMenuOpen ? 'flex' : 'hidden'} flex-col gap-3 border-t border-white/10 pt-4 md:flex md:flex-row md:items-center md:justify-end md:border-t-0 md:pt-0`}
          >
            <a
              className="inline-flex min-h-10 items-center gap-2 rounded-lg bg-white/10 px-4 py-2 text-sm font-semibold text-white transition hover:bg-white/15"
              href="/admin/cash-payment"
            >
              <ReceiptText aria-hidden="true" className="h-4 w-4" />
              Dinheiro
            </a>
            <a
              aria-hidden={!isMaster}
              className={`${isMaster ? 'inline-flex' : 'hidden'} min-h-10 items-center gap-2 rounded-lg bg-white/10 px-4 py-2 text-sm font-semibold text-white transition hover:bg-white/15`}
              href="/admin/settings"
              tabIndex={isMaster ? undefined : -1}
            >
              <Settings aria-hidden="true" className="h-4 w-4" />
              Configurações
            </a>
            <a
              aria-hidden={!isMaster}
              className={`${isMaster ? 'inline-flex' : 'hidden'} min-h-10 items-center gap-2 rounded-lg bg-white/10 px-4 py-2 text-sm font-semibold text-white transition hover:bg-white/15`}
              href="/admin/messages"
              tabIndex={isMaster ? undefined : -1}
            >
              <MessageSquareText aria-hidden="true" className="h-4 w-4" />
              Mensagens
            </a>
            <a
              aria-hidden={!isMaster}
              className={`${isMaster ? 'inline-flex' : 'hidden'} min-h-10 items-center gap-2 rounded-lg bg-gold px-4 py-2 text-sm font-bold text-charcoal transition hover:bg-gold/90`}
              href="/admin/draw"
              tabIndex={isMaster ? undefined : -1}
            >
              <Gift aria-hidden="true" className="h-4 w-4" />
              Sorteio
            </a>
            <button
              className="inline-flex min-h-10 items-center gap-2 rounded-lg border border-white/20 px-4 py-2 text-sm font-semibold text-white transition hover:bg-white/10"
              onClick={logout}
              type="button"
            >
              <LogOut aria-hidden="true" className="h-4 w-4" />
              Sair
            </button>
          </nav>
        </div>
      </header>

      <section className="mx-auto max-w-6xl px-6 py-8">
        {isMaster ? (
          <MetricsSummary areValuesVisible={areMetricsVisible} onToggleVisibility={() => setAreMetricsVisible((current) => !current)} summary={summaryQuery.data} />
        ) : null}

        <Card className={`${isMaster ? 'mt-6' : ''} overflow-hidden`}>
          <form className="mb-6 flex flex-col gap-3 md:flex-row md:items-end" onSubmit={submitFilter}>
            <div className="flex-1">
              <TextInput
                id="admin-query-filter"
                label="Buscar por nome ou telefone"
                onChange={(event) => setQueryFilter(event.target.value)}
                placeholder="nome ou (11) 99999-9999"
                value={queryFilter}
              />
            </div>
            <div className="md:w-56">
              <label className="block text-sm font-semibold text-charcoal" htmlFor="admin-transaction-sort">
                Ordenar por
              </label>
              <select
                className="mt-2 min-h-12 w-full rounded-lg border border-line bg-white px-4 text-base text-charcoal outline-none transition focus:border-gold focus:ring-2 focus:ring-gold/20"
                id="admin-transaction-sort"
                onChange={(event) => {
                  setPage(0);
                  setSort(event.target.value as AdminTransactionSort);
                }}
                value={sort}
              >
                {SORT_OPTIONS.filter((option) => isMaster || !option.value.startsWith('totalAmount')).map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="md:w-44">
              <Button type="submit">
                <Search aria-hidden="true" className="h-4 w-4" />
                Buscar
              </Button>
            </div>
          </form>

          {transactionsQuery.isLoading ? <p className="py-10 text-center text-sm text-warm-gray">Carregando transações...</p> : null}

          {transactionsQuery.isError ? (
            <p className="rounded-lg border border-terracotta/30 bg-blush px-4 py-3 text-sm text-terracotta-dark" role="alert">
              Não foi possível carregar as transações.
            </p>
          ) : null}

          {!transactionsQuery.isLoading && !transactionsQuery.isError && transactions.length === 0 ? (
            <p className="py-10 text-center text-sm text-warm-gray">Nenhuma transação encontrada.</p>
          ) : null}

          {transactions.length > 0 ? (
            <TransactionTable
              areSensitiveValuesVisible={isMaster ? areMetricsVisible : true}
              canDeleteCashTransactions={isMaster}
              canResolveCapacityReview={isMaster}
              deletingExternalReference={deleteCashTransactionMutation.variables ?? null}
              downloadingPdfExternalReference={participantPdfMutation.variables?.externalReference ?? null}
              isDeleting={deleteCashTransactionMutation.isPending}
              isDownloadingPdf={participantPdfMutation.isPending}
              isResolvingCapacityReview={capacityReviewMutation.isPending}
              onDeleteCashTransaction={(transaction) => {
                const confirmed = window.confirm(
                  areMetricsVisible
                    ? `Excluir a transação em dinheiro de ${transaction.name}?`
                    : 'Excluir esta transação em dinheiro?',
                );
                if (confirmed) {
                  deleteCashTransactionMutation.mutate(transaction.externalReference);
                }
              }}
              onDownloadParticipantPdf={(transaction) => participantPdfMutation.mutate(transaction)}
              onResolveCapacityReview={(transaction, decision) =>
                capacityReviewMutation.mutate({ decision, transaction })
              }
              resolvingCapacityReviewReference={capacityReviewMutation.variables?.transaction.externalReference ?? null}
              showFinancialValues={isMaster}
              transactions={transactions}
            />
          ) : null}

          {participantPdfMutation.isError ? (
            <p className="mt-4 rounded-lg border border-terracotta/30 bg-blush px-4 py-3 text-sm text-terracotta-dark" role="alert">
              Não foi possível baixar o PDF do participante.
            </p>
          ) : null}

          {capacityReviewMutation.isError ? (
            <p className="mt-4 rounded-lg border border-terracotta/30 bg-blush px-4 py-3 text-sm text-terracotta-dark" role="alert">
              Não foi possível registrar a decisão administrativa.
            </p>
          ) : null}

          {transactionsQuery.data ? (
            <div className="mt-6 flex items-center justify-between gap-4 border-t border-[#EEE6DF] pt-4">
              <p className="text-sm text-warm-gray">
                Página {transactionsQuery.data.number + 1} de {Math.max(transactionsQuery.data.totalPages, 1)}
              </p>
              <div className="flex gap-2">
                <button
                  className="rounded-lg border border-[#DDD2CB] px-4 py-2 text-sm font-semibold text-charcoal disabled:cursor-not-allowed disabled:opacity-40"
                  disabled={transactionsQuery.data.first}
                  onClick={() => setPage((current) => Math.max(0, current - 1))}
                  type="button"
                >
                  Anterior
                </button>
                <button
                  className="rounded-lg border border-[#DDD2CB] px-4 py-2 text-sm font-semibold text-charcoal disabled:cursor-not-allowed disabled:opacity-40"
                  disabled={transactionsQuery.data.last}
                  onClick={() => setPage((current) => current + 1)}
                  type="button"
                >
                  Próxima
                </button>
              </div>
            </div>
          ) : null}
        </Card>
      </section>
    </main>
  );
}

function MetricsSummary({
  areValuesVisible,
  onToggleVisibility,
  summary,
}: {
  areValuesVisible: boolean;
  onToggleVisibility: () => void;
  summary?: AdminTransactionSummaryResponse;
}) {
  const values = [
    { label: 'Transações', value: String(summary?.totalTransactions ?? 0) },
    { label: 'Números aprovados', value: String(summary?.approvedLuckyNumbers ?? 0) },
    { label: 'Receita aprovada', value: formatCurrency(summary?.approvedRevenue ?? 0) },
  ];

  return (
    <section aria-label="Resumo geral" className="overflow-hidden rounded-lg bg-white shadow-soft">
      <div className="flex items-center justify-between border-b border-[#EEE6DF] px-4 py-3 sm:px-6">
        <p className="text-sm font-bold text-charcoal">Resumo geral</p>
        <button
          aria-label={areValuesVisible ? 'Ocultar valores' : 'Mostrar valores'}
          className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-warm-gray transition hover:bg-cream hover:text-charcoal focus-visible:outline focus-visible:outline-2 focus-visible:outline-terracotta"
          onClick={onToggleVisibility}
          title={areValuesVisible ? 'Ocultar valores' : 'Mostrar valores'}
          type="button"
        >
          {areValuesVisible ? <EyeOff aria-hidden="true" className="h-4 w-4" /> : <Eye aria-hidden="true" className="h-4 w-4" />}
        </button>
      </div>
      <div className="grid grid-cols-3 divide-x divide-[#EEE6DF]">
        {values.map((metric) => (
          <div className="flex min-w-0 flex-col items-center px-2 py-4 text-center sm:px-5 sm:py-5" key={metric.label}>
            <p className="flex min-h-8 items-center text-[10px] font-bold uppercase tracking-wide text-warm-gray sm:text-xs">{metric.label}</p>
            <p className="mt-2 break-words font-serif text-lg font-bold tabular-nums text-charcoal sm:text-2xl" aria-live="polite">
              {areValuesVisible ? metric.value : '****'}
            </p>
          </div>
        ))}
      </div>
    </section>
  );
}

function TransactionTable({
  areSensitiveValuesVisible,
  canDeleteCashTransactions,
  canResolveCapacityReview,
  deletingExternalReference,
  downloadingPdfExternalReference,
  isDeleting,
  isDownloadingPdf,
  isResolvingCapacityReview,
  onDeleteCashTransaction,
  onDownloadParticipantPdf,
  onResolveCapacityReview,
  resolvingCapacityReviewReference,
  showFinancialValues,
  transactions,
}: {
  areSensitiveValuesVisible: boolean;
  canDeleteCashTransactions: boolean;
  canResolveCapacityReview: boolean;
  deletingExternalReference: string | null;
  downloadingPdfExternalReference: string | null;
  isDeleting: boolean;
  isDownloadingPdf: boolean;
  isResolvingCapacityReview: boolean;
  onDeleteCashTransaction: (transaction: AdminTransactionResponse) => void;
  onDownloadParticipantPdf: (transaction: AdminTransactionResponse) => void;
  onResolveCapacityReview: (transaction: AdminTransactionResponse, decision: CapacityReviewDecision) => void;
  resolvingCapacityReviewReference: string | null;
  showFinancialValues: boolean;
  transactions: AdminTransactionResponse[];
}) {
  const [expandedTransactions, setExpandedTransactions] = useState<Set<string>>(() => new Set());

  const toggleTransaction = (externalReference: string) => {
    setExpandedTransactions((current) => {
      const next = new Set(current);

      if (next.has(externalReference)) {
        next.delete(externalReference);
      } else {
        next.add(externalReference);
      }

      return next;
    });
  };

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full text-left text-sm">
        <thead className="border-b border-[#E7DDD6] text-xs uppercase text-warm-gray">
          <tr>
            <th className="px-3 py-3 font-bold">Nome</th>
            <th className="px-3 py-3 font-bold">Data</th>
            <th className="px-3 py-3 font-bold">Contato</th>
            <th className="px-3 py-3 font-bold">Método</th>
            <th className="px-3 py-3 font-bold">Qtd.</th>
            {showFinancialValues ? <th className="px-3 py-3 font-bold">Total</th> : null}
            <th className="px-3 py-3 font-bold">Status</th>
            <th className="px-3 py-3 font-bold">Números</th>
            <th className="px-3 py-3 text-right font-bold">Ações</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-[#EEE6DF]">
          {transactions.map((transaction) => {
            const hasMoreLuckyNumbers = transaction.luckyNumbers.length > 8;
            const isExpanded = expandedTransactions.has(transaction.externalReference);
            const displayedLuckyNumbers = isExpanded ? transaction.luckyNumbers : transaction.luckyNumbers.slice(0, 8);
            const canDownloadParticipantPdf = transaction.luckyNumbers.length > 0;
            const maskedValue = '****';

            return (
            <tr
              aria-expanded={hasMoreLuckyNumbers ? isExpanded : undefined}
              className={hasMoreLuckyNumbers ? 'cursor-pointer hover:bg-blush/40 focus-visible:outline focus-visible:outline-2 focus-visible:outline-terracotta' : undefined}
              key={transaction.externalReference}
              onClick={hasMoreLuckyNumbers ? () => toggleTransaction(transaction.externalReference) : undefined}
              onKeyDown={
                hasMoreLuckyNumbers
                  ? (event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        toggleTransaction(transaction.externalReference);
                      }
                    }
                  : undefined
              }
              role={hasMoreLuckyNumbers ? 'button' : undefined}
              tabIndex={hasMoreLuckyNumbers ? 0 : undefined}
            >
              <td className="px-3 py-4 font-medium text-charcoal">{areSensitiveValuesVisible ? transaction.name : maskedValue}</td>
              <td className="px-3 py-4 text-warm-gray">{formatDateTime(transaction.createdAt)}</td>
              <td className="px-3 py-4 text-warm-gray">
                <span className="block">{areSensitiveValuesVisible ? formatPhoneNumber(transaction.phone) || '-' : maskedValue}</span>
              </td>
              <td className="px-3 py-4 text-warm-gray">{transaction.paymentMethod === 'CASH' ? 'Dinheiro' : 'Mercado Pago'}</td>
              <td className="px-3 py-4 text-warm-gray">{areSensitiveValuesVisible ? transaction.quantity : maskedValue}</td>
              {showFinancialValues ? (
                <td className="px-3 py-4 text-warm-gray">{areSensitiveValuesVisible && transaction.totalAmount ? formatCurrency(transaction.totalAmount) : maskedValue}</td>
              ) : null}
              <td className="px-3 py-4">
                <StatusBadge capacityReviewStatus={transaction.capacityReviewStatus} status={transaction.status} />
              </td>
              <td className="w-80 min-w-80 px-3 py-4">
                {displayedLuckyNumbers.length > 0 ? (
                  <div className="grid w-full grid-cols-4 gap-2">
                    {displayedLuckyNumbers.map((number) => (
                      <span
                        className="flex min-w-0 items-center justify-center whitespace-nowrap rounded-md bg-gold/20 px-2 py-1 font-mono text-xs font-bold tabular-nums text-charcoal"
                        key={number}
                        title={number}
                      >
                        {number}
                      </span>
                    ))}
                  </div>
                ) : (
                  <span className="text-warm-gray">-</span>
                )}
                {hasMoreLuckyNumbers ? (
                  <ChevronDown
                    aria-hidden="true"
                    className={`mt-2 h-4 w-4 text-terracotta transition-transform ${isExpanded ? 'rotate-180' : ''}`}
                  />
                ) : null}
              </td>
              <td className="px-3 py-4 text-right">
                <div className="flex items-center justify-end gap-2">
                {canResolveCapacityReview && transaction.capacityReviewStatus === 'PENDING' ? (
                  <div className="flex min-w-44 flex-col gap-2">
                    <button
                      className="rounded-lg border border-terracotta/30 px-3 py-2 text-xs font-bold text-terracotta-dark transition hover:bg-blush disabled:opacity-50"
                      disabled={isResolvingCapacityReview && resolvingCapacityReviewReference === transaction.externalReference}
                      onClick={(event) => {
                        event.stopPropagation();
                        onResolveCapacityReview(transaction, 'REFUND_COMPLETED');
                      }}
                      type="button"
                    >
                      Reembolso realizado manualmente
                    </button>
                    <button
                      className="rounded-lg border border-green/30 px-3 py-2 text-xs font-bold text-green transition hover:bg-ivory-deep disabled:opacity-50"
                      disabled={isResolvingCapacityReview && resolvingCapacityReviewReference === transaction.externalReference}
                      onClick={(event) => {
                        event.stopPropagation();
                        onResolveCapacityReview(transaction, 'CONTRIBUTION_WITHOUT_NUMBERS');
                      }}
                      type="button"
                    >
                      Contribuição mantida sem números
                    </button>
                  </div>
                ) : null}
                  <button
                    aria-label={
                      areSensitiveValuesVisible
                        ? `Baixar PDF dos números de ${transaction.name}`
                        : 'Baixar PDF dos números'
                    }
                    className="inline-flex min-h-9 items-center justify-center rounded-lg border border-green/30 px-3 py-2 text-xs font-bold text-green transition hover:bg-ivory-deep disabled:cursor-not-allowed disabled:opacity-50"
                    disabled={
                      !canDownloadParticipantPdf ||
                      (isDownloadingPdf && downloadingPdfExternalReference === transaction.externalReference)
                    }
                    onClick={(event) => {
                      event.stopPropagation();
                      onDownloadParticipantPdf(transaction);
                    }}
                    title="Baixar PDF com todos os números do participante"
                    type="button"
                  >
                    <Download aria-hidden="true" className="h-4 w-4" />
                  </button>
                {canDeleteCashTransactions && transaction.paymentMethod === 'CASH' ? (
                  <button
                    aria-label={
                      areSensitiveValuesVisible ? `Excluir transação de ${transaction.name}` : 'Excluir transação'
                    }
                    className="inline-flex min-h-9 items-center justify-center rounded-lg border border-terracotta/30 px-3 py-2 text-xs font-bold text-terracotta-dark transition hover:bg-blush disabled:cursor-not-allowed disabled:opacity-50"
                    disabled={isDeleting && deletingExternalReference === transaction.externalReference}
                    onClick={(event) => {
                      event.stopPropagation();
                      onDeleteCashTransaction(transaction);
                    }}
                    type="button"
                  >
                    <Trash2 aria-hidden="true" className="h-4 w-4" />
                  </button>
                ) : (
                  null
                )}
                </div>
              </td>
            </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function downloadPdf(pdf: Blob, filename: string) {
  const url = window.URL.createObjectURL(pdf);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  window.URL.revokeObjectURL(url);
}

function shortReference(externalReference: string) {
  const sanitizedReference = externalReference.replace(/[^A-Za-z0-9]/g, '');
  return sanitizedReference.length <= 8 ? sanitizedReference : sanitizedReference.slice(0, 8);
}

function StatusBadge({
  capacityReviewStatus,
  status,
}: {
  capacityReviewStatus: AdminTransactionResponse['capacityReviewStatus'];
  status: AdminTransactionResponse['status'];
}) {
  const styles = {
    APROVADO: 'bg-olive/15 text-olive',
    PENDENTE: 'bg-gold/15 text-[#8A6A00]',
    REJEITADO: 'bg-terracotta/15 text-terracotta-dark',
    CANCELADO: 'bg-terracotta/15 text-terracotta-dark',
    ESTORNADO: 'bg-terracotta/15 text-terracotta-dark',
    CHARGEBACK: 'bg-terracotta/15 text-terracotta-dark',
    EM_MEDIACAO: 'bg-gold/15 text-[#8A6A00]',
  };

  if (capacityReviewStatus) {
    const reviewLabels = {
      PENDING: 'REVISÃO DE CAPACIDADE',
      REFUND_COMPLETED: 'REEMBOLSO REALIZADO MANUALMENTE',
      CONTRIBUTION_WITHOUT_NUMBERS: 'CONTRIBUIÇÃO MANTIDA SEM NÚMEROS',
    };
    const reviewStyle = capacityReviewStatus === 'CONTRIBUTION_WITHOUT_NUMBERS'
      ? 'bg-olive/15 text-olive'
      : capacityReviewStatus === 'PENDING'
        ? 'bg-gold/15 text-[#8A6A00]'
        : 'bg-terracotta/15 text-terracotta-dark';
    return <span className={`rounded-full px-3 py-1 text-xs font-bold ${reviewStyle}`}>{reviewLabels[capacityReviewStatus]}</span>;
  }

  return <span className={`rounded-full px-3 py-1 text-xs font-bold ${styles[status]}`}>{status}</span>;
}
