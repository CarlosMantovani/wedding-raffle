import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, MessageSquareText } from 'lucide-react';
import { useState } from 'react';

import { Card } from '../../components/ui/Card';
import { adminTransactionService } from '../../services/adminTransactionService';
import { formatDateTime } from '../../utils/formatters';

const PAGE_SIZE = 20;

export function AdminMessagesPage() {
  const [page, setPage] = useState(0);
  const messagesQuery = useQuery({
    queryKey: ['admin-gift-messages', page],
    queryFn: () =>
      adminTransactionService.listGiftMessages({
        page,
        size: PAGE_SIZE,
        sort: 'createdAt,desc',
      }),
  });
  const messages = messagesQuery.data?.content ?? [];

  return (
    <main className="min-h-screen bg-cream text-charcoal">
      <header className="bg-charcoal px-6 py-4 text-white">
        <div className="mx-auto flex max-w-4xl items-center justify-between gap-4">
          <div>
            <p className="font-serif text-2xl font-bold">
              Presente <span className="italic text-gold">Premiado</span>
            </p>
            <p className="mt-1 text-xs text-white/55">Mensagens para o casal</p>
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

      <section className="mx-auto max-w-4xl px-6 py-8">
        <Card className="space-y-5">
          <div className="flex items-center gap-3">
            <span className="grid h-11 w-11 place-items-center rounded-lg bg-blush text-terracotta">
              <MessageSquareText aria-hidden="true" className="h-5 w-5" />
            </span>
            <div>
              <h1 className="font-serif text-2xl font-bold">Mensagens</h1>
              <p className="text-sm text-warm-gray">Recados enviados junto com as compras.</p>
            </div>
          </div>

          {messagesQuery.isLoading ? (
            <p className="py-10 text-center text-sm text-warm-gray">Carregando mensagens...</p>
          ) : null}

          {messagesQuery.isError ? (
            <p className="rounded-lg border border-terracotta/30 bg-blush px-4 py-3 text-sm text-terracotta-dark" role="alert">
              Não foi possível carregar as mensagens.
            </p>
          ) : null}

          {!messagesQuery.isLoading && !messagesQuery.isError && messages.length === 0 ? (
            <p className="py-10 text-center text-sm text-warm-gray">Nenhuma mensagem enviada ainda.</p>
          ) : null}

          <div className="space-y-3">
            {messages.map((message) => (
              <article className="rounded-lg border border-line bg-white/70 p-4" key={message.externalReference}>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <h2 className="font-serif text-lg font-bold text-charcoal">{message.name}</h2>
                  <time className="text-xs font-semibold text-warm-gray" dateTime={message.createdAt}>
                    {formatDateTime(message.createdAt)}
                  </time>
                </div>
                <p className="mt-3 whitespace-pre-wrap break-words text-sm leading-relaxed text-charcoal">
                  {message.giftMessage}
                </p>
              </article>
            ))}
          </div>

          {messagesQuery.data ? (
            <div className="flex items-center justify-between gap-4 border-t border-[#EEE6DF] pt-4">
              <p className="text-sm text-warm-gray">
                Página {messagesQuery.data.number + 1} de {Math.max(messagesQuery.data.totalPages, 1)}
              </p>
              <div className="flex gap-2">
                <button
                  className="rounded-lg border border-[#DDD2CB] px-4 py-2 text-sm font-semibold text-charcoal disabled:cursor-not-allowed disabled:opacity-40"
                  disabled={messagesQuery.data.first}
                  onClick={() => setPage((current) => Math.max(0, current - 1))}
                  type="button"
                >
                  Anterior
                </button>
                <button
                  className="rounded-lg border border-[#DDD2CB] px-4 py-2 text-sm font-semibold text-charcoal disabled:cursor-not-allowed disabled:opacity-40"
                  disabled={messagesQuery.data.last}
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
