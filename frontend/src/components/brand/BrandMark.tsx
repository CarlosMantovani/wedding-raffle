import { Heart } from 'lucide-react';

export function BrandMark() {
  return (
    <div className="text-center">
      <svg aria-hidden="true" className="mx-auto mb-4 h-16 w-28" viewBox="0 0 130 82" fill="none">
        <circle cx="45" cy="41" r="30" stroke="#B8935A" strokeWidth="5.5" fill="none" opacity="0.88" />
        <circle cx="85" cy="41" r="30" stroke="#B8935A" strokeWidth="5.5" fill="none" opacity="0.88" />
        <path d="M65 15 L69 23 L65 31 L61 23 Z" fill="#B8935A" opacity="0.72" />
      </svg>
      <h1 className="font-serif text-4xl font-bold leading-tight text-green sm:text-5xl">
        <span className="max-[364px]:block">Paula</span>
        <Heart
          aria-label="e"
          className="mx-2 mb-1 inline-block h-6 w-6 text-wine max-[364px]:mx-auto max-[364px]:my-1 max-[364px]:block sm:h-7 sm:w-7"
          strokeWidth={1.5}
        />
        <span className="max-[364px]:block">José Carlos</span>
      </h1>
      <p className="block text-base font-semibold italic m-2 text-inkSoft " >
          Sua sorte, nossa Lua de Mel!
      </p>
    </div>
  );
}

export function GoldDivider() {
  return (
    <div aria-hidden="true" className="mx-auto flex max-w-56 items-center gap-3">
      <div className="h-px flex-1 bg-gradient-to-r from-transparent to-gold/60" />
      <div className="h-1.5 w-1.5 rotate-45 bg-gold" />
      <div className="h-px flex-1 bg-gradient-to-l from-transparent to-gold/60" />
    </div>
  );
}
