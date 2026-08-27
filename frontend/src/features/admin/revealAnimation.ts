import type { RaffleCandidateResponse } from '../../types/admin';

export const REVEAL_DURATION_MS = 10000;
export const REVEAL_INITIAL_TICK_MS = 90;
export const REVEAL_FINAL_TICK_MS = 450;
export const REVEAL_WINNER_HOLD_MS = 900;

export function runRevealAnimation(
  candidates: RaffleCandidateResponse[],
  winnerCandidate: RaffleCandidateResponse,
  setCandidate: (candidate: RaffleCandidateResponse) => void,
) {
  const revealCandidates = candidates.length > 0 ? candidates : [winnerCandidate];

  return new Promise<void>((resolve) => {
    let index = 0;
    const startedAt = performance.now();
    setCandidate(revealCandidates[index]);

    const scheduleNextCandidate = () => {
      const elapsed = performance.now() - startedAt;
      const progress = Math.min(elapsed / REVEAL_DURATION_MS, 1);
      if (progress >= 1) {
        setCandidate(winnerCandidate);
        window.setTimeout(resolve, REVEAL_WINNER_HOLD_MS);
        return;
      }

      index = (index + 1) % revealCandidates.length;
      setCandidate(revealCandidates[index]);

      const easedProgress = progress ** 2;
      const nextTick =
        REVEAL_INITIAL_TICK_MS + (REVEAL_FINAL_TICK_MS - REVEAL_INITIAL_TICK_MS) * easedProgress;
      window.setTimeout(scheduleNextCandidate, nextTick);
    };

    window.setTimeout(scheduleNextCandidate, REVEAL_INITIAL_TICK_MS);
  });
}
