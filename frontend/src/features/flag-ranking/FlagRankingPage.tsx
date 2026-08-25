import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, RefreshCw, Trophy } from 'lucide-react';

import { BrandMark, GoldDivider } from '../../components/brand/BrandMark';
import { Card } from '../../components/ui/Card';
import { homeService } from '../../services/homeService';
import { CountdownPanel } from '../buy-numbers/CountdownPanel';
import { FlagRankingList } from './FlagRankingList';

const FLAG_RANKING_REFRESH_INTERVAL_MS = 5 * 60 * 1000;

export function FlagRankingPage() {
  const homeSummaryQuery = useQuery({
    queryKey: ['home-summary'],
    queryFn: homeService.getSummary,
  });
  const flagRankingQuery = useQuery({
    queryKey: ['flag-ranking', 'top-15'],
    queryFn: homeService.getFlagRanking,
    refetchInterval: FLAG_RANKING_REFRESH_INTERVAL_MS,
  });

  return (
    <main className="min-h-screen bg-cream px-6 pb-16 pt-10 text-charcoal">
      <div className="mx-auto flex w-full max-w-[720px] flex-col gap-7">
        <header className="text-center">
          <BrandMark />
          <p className="mx-auto mt-4 max-w-sm text-sm leading-relaxed text-warm-gray">
            Acompanhe as bandeiras que mais somaram números da sorte.
          </p>
          <div className="mt-6">
            <GoldDivider />
          </div>
        </header>

        <a
          className="inline-flex w-fit items-center gap-2 rounded-lg border border-green px-4 py-2 text-sm font-bold text-green transition hover:bg-ivory-deep focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green"
          href="/"
        >
          <ArrowLeft aria-hidden="true" className="h-4 w-4" />
          Voltar
        </a>

        <CountdownPanel scheduledDrawAt={homeSummaryQuery.data?.scheduledDrawAt ?? null} />

        <Card className="bg-white/90">
          <div className="space-y-5">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <p className="inline-flex items-center gap-2 text-xs font-bold uppercase tracking-wide text-green">
                  <Trophy aria-hidden="true" className="h-4 w-4" />
                  Disputa das bandeiras
                </p>
                <h1 className="mt-2 font-serif text-3xl font-bold text-charcoal">Top 15 bandeiras</h1>
              </div>

              <p className="inline-flex items-center gap-2 rounded-lg bg-ivory-deep px-3 py-2 text-xs font-semibold text-warm-gray">
                <RefreshCw aria-hidden="true" className="h-4 w-4" />
                Atualiza a cada 5 minutos
              </p>
            </div>

            {flagRankingQuery.isError ? (
              <p className="rounded-lg border border-wine/30 bg-white px-4 py-3 text-sm text-wine" role="alert">
                Não foi possível carregar o ranking.
              </p>
            ) : null}

            <FlagRankingList
              isLoading={flagRankingQuery.isLoading}
              ranking={flagRankingQuery.data ?? []}
            />
          </div>
        </Card>
      </div>
    </main>
  );
}
