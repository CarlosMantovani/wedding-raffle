/// <reference types="node" />

import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

import { FlagEmoji } from './FlagEmoji';

const participantFlags = readFileSync(
  resolve(process.cwd(), '../backend/src/main/resources/participant-flags.csv'),
  'utf8',
)
  .split(/\r?\n/)
  .filter((line) => line && !line.startsWith('#'))
  .map((line) => {
    const [code, name, symbol] = line.split(';');
    return { code, name, symbol };
  });

describe('FlagEmoji', () => {
  it('keeps the expanded catalog unchanged', () => {
    expect(participantFlags).toHaveLength(222);
  });

  it.each(participantFlags)('loads $code ($name) as an SVG image on demand', async ({ symbol }) => {
    render(<FlagEmoji emoji={symbol} />);

    const flag = await waitForFlagImage(symbol);

    expect(flag).toHaveAttribute('src');
    expect(flag).toHaveClass('object-contain');
  });

  it('loads different country and state identities independently', async () => {
    render(
      <>
        <FlagEmoji emoji="🇧🇷" />
        <FlagEmoji emoji="🇨🇦" />
        <FlagEmoji emoji="BR-SP" />
      </>,
    );

    const flags = await Promise.all([
      waitForFlagImage('🇧🇷'),
      waitForFlagImage('🇨🇦'),
      waitForFlagImage('BR-SP'),
    ]);

    expect(new Set(flags.map((flag) => flag.getAttribute('src'))).size).toBe(3);
  });

  it('reuses the same asset URL for repeated identities', async () => {
    render(
      <>
        <FlagEmoji emoji="🇧🇷" />
        <FlagEmoji emoji="🇧🇷" />
      </>,
    );

    await waitFor(() => {
      expect(screen.getAllByRole('img', { name: '🇧🇷' })).toHaveLength(2);
      expect(
        screen.getAllByRole('img', { name: '🇧🇷' }).every((flag) => flag.tagName === 'IMG'),
      ).toBe(true);
    });

    const [firstFlag, secondFlag] = screen.getAllByRole('img', { name: '🇧🇷' });
    expect(firstFlag).toHaveAttribute('src', secondFlag.getAttribute('src'));
  });

  it('keeps the text fallback for an unknown identity code', () => {
    render(<FlagEmoji emoji="UNKNOWN-FLAG" />);

    const fallback = screen.getByRole('img', { name: 'UNKNOWN-FLAG' });

    expect(fallback.tagName).toBe('SPAN');
    expect(fallback).toHaveTextContent('UNKNOWN-FLAG');
  });

  it('falls back after an asset fails to render without retrying', async () => {
    render(<FlagEmoji emoji="BR-SP" />);

    fireEvent.error(await waitForFlagImage('BR-SP'));

    const fallback = screen.getByRole('img', { name: 'BR-SP' });
    expect(fallback.tagName).toBe('SPAN');
    expect(fallback).toHaveTextContent('BR-SP');
  });
});

async function waitForFlagImage(symbol: string) {
  return waitFor(() => {
    const flag = screen.getByRole('img', { name: symbol });
    expect(flag.tagName).toBe('IMG');
    return flag;
  });
}
