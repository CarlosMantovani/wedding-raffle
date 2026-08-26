import { useEffect, useState } from 'react';

import { getCountdownMs, getCountdownParts, isPastDateTime, isValidDateTime } from '../../utils/dateTime';
import { formatDateTime } from '../../utils/formatters';

const URGENCY_THRESHOLDS = {
  oneHour: 60 * 60 * 1000,
  thirtyMinutes: 30 * 60 * 1000,
  fifteenMinutes: 15 * 60 * 1000,
  fiveMinutes: 5 * 60 * 1000,
};

export function CountdownPanel({ scheduledDrawAt }: { scheduledDrawAt: string | null }) {
  const [, setTick] = useState(0);

  useEffect(() => {
    if (!scheduledDrawAt) return undefined;

    const intervalId = window.setInterval(() => setTick((current) => current + 1), 1000);
    return () => window.clearInterval(intervalId);
  }, [scheduledDrawAt]);

  if (!scheduledDrawAt || !isValidDateTime(scheduledDrawAt)) return null;

  if (isPastDateTime(scheduledDrawAt)) {
    return (
      <section className="rounded-lg bg-green-deep p-6 text-center text-white">
        <p className="text-xs font-bold uppercase tracking-wide text-gold">Sorteio</p>
        <h2 className="mt-2 font-serif text-2xl font-bold">Últimos instantes</h2>
        <p className="mt-2 text-xs font-semibold text-white/70">Sorteio em {formatDateTime(scheduledDrawAt)}</p>
      </section>
    );
  }

  const countdown = getCountdownParts(scheduledDrawAt);
  const urgency = getUrgencyLevel(getCountdownMs(scheduledDrawAt));

  return (
    <section className={`rounded-lg p-6 ${urgency.cardClassName} text-center text-white transition-colors`}>
      <p className={`text-xs font-bold uppercase tracking-wide ${urgency.eyebrowClassName}`}>Contagem para o sorteio</p>
      <h2 className="mt-2 font-serif text-2xl font-bold">{urgency.title}</h2>
      <p className="mt-2 text-xs font-semibold text-white/70">Sorteio em {formatDateTime(scheduledDrawAt)}</p>
      <div className="mt-4 grid grid-cols-4 gap-2">
        <CountdownItem label="Dias" value={countdown.days} />
        <CountdownItem label="Horas" value={countdown.hours} />
        <CountdownItem label="Min." value={countdown.minutes} />
        <CountdownItem label="Seg." value={countdown.seconds} />
      </div>
      {urgency.message ? (
        <p className={`mt-4 rounded-lg px-3 py-2 text-sm font-semibold ${urgency.messageClassName}`}>
          {urgency.message}
        </p>
      ) : null}
    </section>
  );
}

function CountdownItem({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg bg-white/10 px-2 py-3">
      <span className="block font-serif text-2xl font-bold leading-none">{String(value).padStart(2, '0')}</span>
      <span className="mt-1 block text-[11px] font-semibold uppercase text-white/60">{label}</span>
    </div>
  );
}

function getUrgencyLevel(diffMs: number) {
  if (diffMs <= URGENCY_THRESHOLDS.fiveMinutes) {
    return {
      cardClassName: 'bg-wine ring-2 ring-gold',
      eyebrowClassName: 'text-gold',
      message: 'Últimos 5 minutos para garantir seus números.',
      messageClassName: 'animate-pulse bg-green text-white',
      title: 'Última chamada',
    };
  }

  if (diffMs <= URGENCY_THRESHOLDS.fifteenMinutes) {
    return {
      cardClassName: 'bg-wine',
      eyebrowClassName: 'text-gold',
      message: 'Faltam menos de 15 minutos para o sorteio.',
      messageClassName: 'bg-white/15 text-white',
      title: 'Reta final',
    };
  }

  if (diffMs <= URGENCY_THRESHOLDS.thirtyMinutes) {
    return {
      cardClassName: 'bg-green-deep',
      eyebrowClassName: 'text-white',
      message: 'Faltam menos de 30 minutos. Não deixe para depois.',
      messageClassName: 'bg-white/15 text-white',
      title: 'Pouco tempo restante',
    };
  }

  if (diffMs <= URGENCY_THRESHOLDS.oneHour) {
    return {
      cardClassName: 'bg-green',
      eyebrowClassName: 'text-white',
      message: 'Falta menos de 1 hora para o sorteio.',
      messageClassName: 'bg-white/15 text-white',
      title: 'O sorteio está chegando',
    };
  }

  return {
    cardClassName: 'bg-green-deep',
    eyebrowClassName: 'text-gold',
    message: null,
    messageClassName: 'bg-white/10 text-white/80',
    title: 'Tempo restante',
  };
}
