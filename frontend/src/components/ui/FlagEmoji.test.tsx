/// <reference types="node" />

import { render, screen } from '@testing-library/react';
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
  it.each(participantFlags)('renders $code ($name) as a bundled SVG image', ({ symbol }) => {
    render(<FlagEmoji emoji={symbol} />);

    const flag = screen.getByRole('img', { name: symbol });

    expect(flag.tagName).toBe('IMG');
    expect(flag).toHaveAttribute('src');
    expect(flag).toHaveClass('object-contain');
  });

  it('keeps the text fallback for an unknown visual identity', () => {
    render(<FlagEmoji emoji="UNKNOWN-FLAG" />);

    const fallback = screen.getByRole('img', { name: 'UNKNOWN-FLAG' });

    expect(fallback.tagName).toBe('SPAN');
    expect(fallback).toHaveTextContent('UNKNOWN-FLAG');
  });
});
