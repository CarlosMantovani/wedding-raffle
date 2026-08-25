import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import type { FlagRankingItem } from '../../types/home';
import { FlagRankingList } from './FlagRankingList';

const ranking: FlagRankingItem[] = [
  {
    code: 'BRAZIL',
    emoji: '🇧🇷',
    name: 'Brasil',
    position: 1,
    progressPercent: 100,
  },
  {
    code: 'CANADA',
    emoji: '🇨🇦',
    name: 'Canadá',
    position: 2,
    progressPercent: 75,
  },
  {
    code: 'BRAZIL_SAO_PAULO',
    emoji: 'BR-SP',
    name: 'São Paulo',
    position: 3,
    progressPercent: 50,
  },
];

describe('FlagRankingList', () => {
  it('loads only the identities rendered by a ranking', async () => {
    render(<FlagRankingList isLoading={false} ranking={ranking} />);

    await waitFor(() => {
      const flags = ranking.flatMap((item) => screen.getAllByRole('img', { name: item.emoji }));
      expect(flags).toHaveLength(6);
      expect(flags.every((flag) => flag.tagName === 'IMG')).toBe(true);
    });
  });
});
