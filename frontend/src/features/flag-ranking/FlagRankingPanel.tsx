import { ChevronRight, Clock, Flag, RotateCcw, Trophy } from 'lucide-react';
import type { ReactNode } from 'react';

import { Card } from '../../components/ui/Card';
import type { FlagRankingItem } from '../../types/home';
import { FlagRankingList } from './FlagRankingList';

interface FlagRankingPanelProps {
  isLoading: boolean;
  ranking: FlagRankingItem[];
}

export function FlagRankingPanel({ isLoading, ranking }: FlagRankingPanelProps) {
  return (
    <aside>
      <Card className="bg-white/90">
        <div className="space-y-4">
          <div>
            <p className="text-xs font-bold uppercase tracking-wide text-green">Disputa das bandeiras</p>
            <h2 className="mt-2 font-serif text-2xl font-bold text-charcoal">Ranking de bandeiras</h2>
            <div className="mt-4 grid gap-2">
              <FlagRule icon={<Flag aria-hidden="true" className="h-4 w-4" />} text="Uma bandeira exclusiva por telefone." />
              <FlagRule
                icon={<RotateCcw aria-hidden="true" className="h-4 w-4" />}
                text="Novas compras somam pontos na mesma bandeira."
              />
              <FlagRule
                icon={<Clock aria-hidden="true" className="h-4 w-4" />}
                text="Em caso de empate de bandeiras, a compra mais recente fica na frente"
              />
              <FlagRule
                icon={<Trophy aria-hidden="true" className="h-4 w-4" />}
                text="A líder também ganhará um prêmio especial."
              />
            </div>
          </div>

          <FlagRankingList isLoading={isLoading} ranking={ranking} />

          <a
            className="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-lg border border-green bg-transparent px-5 py-3 text-sm font-semibold text-green transition hover:bg-ivory-deep focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-green"
            href="/flag-ranking"
          >
            Ver top 30
            <ChevronRight aria-hidden="true" className="h-4 w-4" />
          </a>
        </div>
      </Card>
    </aside>
  );
}

function FlagRule({ icon, text }: { icon: ReactNode; text: string }) {
  return (
    <div className="flex items-center gap-3 rounded-lg bg-ivory-deep/70 px-3 py-2 text-sm font-medium text-charcoal">
      <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-white text-green">{icon}</span>
      <span className="leading-snug">{text}</span>
    </div>
  );
}
