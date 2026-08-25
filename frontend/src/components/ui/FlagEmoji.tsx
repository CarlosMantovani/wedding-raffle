import twemoji from 'twemoji';
import { useEffect, useState } from 'react';

type FlagAssetLoader = () => Promise<string>;

const flagAssetModules = import.meta.glob<string>(
  [
    '/node_modules/@twemoji/svg/1f1e6-1f1e9.svg',
    '/node_modules/@twemoji/svg/1f1e6-1f1ea.svg',
    '/node_modules/@twemoji/svg/1f1e6-1f1eb.svg',
    '/node_modules/@twemoji/svg/1f1e6-1f1ec.svg',
    '/node_modules/@twemoji/svg/1f1e6-1f1f1.svg',
    '/node_modules/@twemoji/svg/1f1e6-1f1f2.svg',
    '/node_modules/@twemoji/svg/1f1e6-1f1f4.svg',
    '/node_modules/@twemoji/svg/1f1e6-1f1f7.svg',
    '/node_modules/@twemoji/svg/1f1e6-1f1f9.svg',
    '/node_modules/@twemoji/svg/1f1e6-1f1fa.svg',
    '/node_modules/@twemoji/svg/1f1e6-1f1ff.svg',
    '/node_modules/@twemoji/svg/1f1e7-1f1e6.svg',
    '/node_modules/@twemoji/svg/1f1e7-1f1e7.svg',
    '/node_modules/@twemoji/svg/1f1e7-1f1e9.svg',
    '/node_modules/@twemoji/svg/1f1e7-1f1ea.svg',
    '/node_modules/@twemoji/svg/1f1e7-1f1eb.svg',
    '/node_modules/@twemoji/svg/1f1e7-1f1ec.svg',
    '/node_modules/@twemoji/svg/1f1e7-1f1ed.svg',
    '/node_modules/@twemoji/svg/1f1e7-1f1ee.svg',
    '/node_modules/@twemoji/svg/1f1e7-1f1ef.svg',
    '/node_modules/@twemoji/svg/1f1e7-1f1f3.svg',
    '/node_modules/@twemoji/svg/1f1e7-1f1f4.svg',
    '/node_modules/@twemoji/svg/1f1e7-1f1f7.svg',
    '/node_modules/@twemoji/svg/1f1e7-1f1f8.svg',
    '/node_modules/@twemoji/svg/1f1e7-1f1f9.svg',
    '/node_modules/@twemoji/svg/1f1e7-1f1fc.svg',
    '/node_modules/@twemoji/svg/1f1e7-1f1fe.svg',
    '/node_modules/@twemoji/svg/1f1e7-1f1ff.svg',
    '/node_modules/@twemoji/svg/1f1e8-1f1e6.svg',
    '/node_modules/@twemoji/svg/1f1e8-1f1e9.svg',
    '/node_modules/@twemoji/svg/1f1e8-1f1eb.svg',
    '/node_modules/@twemoji/svg/1f1e8-1f1ec.svg',
    '/node_modules/@twemoji/svg/1f1e8-1f1ed.svg',
    '/node_modules/@twemoji/svg/1f1e8-1f1ee.svg',
    '/node_modules/@twemoji/svg/1f1e8-1f1f1.svg',
    '/node_modules/@twemoji/svg/1f1e8-1f1f2.svg',
    '/node_modules/@twemoji/svg/1f1e8-1f1f3.svg',
    '/node_modules/@twemoji/svg/1f1e8-1f1f4.svg',
    '/node_modules/@twemoji/svg/1f1e8-1f1f7.svg',
    '/node_modules/@twemoji/svg/1f1e8-1f1fa.svg',
    '/node_modules/@twemoji/svg/1f1e8-1f1fb.svg',
    '/node_modules/@twemoji/svg/1f1e8-1f1fe.svg',
    '/node_modules/@twemoji/svg/1f1e8-1f1ff.svg',
    '/node_modules/@twemoji/svg/1f1e9-1f1ea.svg',
    '/node_modules/@twemoji/svg/1f1e9-1f1ef.svg',
    '/node_modules/@twemoji/svg/1f1e9-1f1f0.svg',
    '/node_modules/@twemoji/svg/1f1e9-1f1f2.svg',
    '/node_modules/@twemoji/svg/1f1e9-1f1f4.svg',
    '/node_modules/@twemoji/svg/1f1e9-1f1ff.svg',
    '/node_modules/@twemoji/svg/1f1ea-1f1e8.svg',
    '/node_modules/@twemoji/svg/1f1ea-1f1ea.svg',
    '/node_modules/@twemoji/svg/1f1ea-1f1ec.svg',
    '/node_modules/@twemoji/svg/1f1ea-1f1f7.svg',
    '/node_modules/@twemoji/svg/1f1ea-1f1f8.svg',
    '/node_modules/@twemoji/svg/1f1ea-1f1f9.svg',
    '/node_modules/@twemoji/svg/1f1eb-1f1ee.svg',
    '/node_modules/@twemoji/svg/1f1eb-1f1ef.svg',
    '/node_modules/@twemoji/svg/1f1eb-1f1f2.svg',
    '/node_modules/@twemoji/svg/1f1eb-1f1f7.svg',
    '/node_modules/@twemoji/svg/1f1ec-1f1e6.svg',
    '/node_modules/@twemoji/svg/1f1ec-1f1e7.svg',
    '/node_modules/@twemoji/svg/1f1ec-1f1e9.svg',
    '/node_modules/@twemoji/svg/1f1ec-1f1ea.svg',
    '/node_modules/@twemoji/svg/1f1ec-1f1ed.svg',
    '/node_modules/@twemoji/svg/1f1ec-1f1f2.svg',
    '/node_modules/@twemoji/svg/1f1ec-1f1f3.svg',
    '/node_modules/@twemoji/svg/1f1ec-1f1f6.svg',
    '/node_modules/@twemoji/svg/1f1ec-1f1f7.svg',
    '/node_modules/@twemoji/svg/1f1ec-1f1f9.svg',
    '/node_modules/@twemoji/svg/1f1ec-1f1fc.svg',
    '/node_modules/@twemoji/svg/1f1ec-1f1fe.svg',
    '/node_modules/@twemoji/svg/1f1ed-1f1f3.svg',
    '/node_modules/@twemoji/svg/1f1ed-1f1f7.svg',
    '/node_modules/@twemoji/svg/1f1ed-1f1f9.svg',
    '/node_modules/@twemoji/svg/1f1ed-1f1fa.svg',
    '/node_modules/@twemoji/svg/1f1ee-1f1e9.svg',
    '/node_modules/@twemoji/svg/1f1ee-1f1ea.svg',
    '/node_modules/@twemoji/svg/1f1ee-1f1f1.svg',
    '/node_modules/@twemoji/svg/1f1ee-1f1f3.svg',
    '/node_modules/@twemoji/svg/1f1ee-1f1f6.svg',
    '/node_modules/@twemoji/svg/1f1ee-1f1f7.svg',
    '/node_modules/@twemoji/svg/1f1ee-1f1f8.svg',
    '/node_modules/@twemoji/svg/1f1ee-1f1f9.svg',
    '/node_modules/@twemoji/svg/1f1ef-1f1f2.svg',
    '/node_modules/@twemoji/svg/1f1ef-1f1f4.svg',
    '/node_modules/@twemoji/svg/1f1ef-1f1f5.svg',
    '/node_modules/@twemoji/svg/1f1f0-1f1ea.svg',
    '/node_modules/@twemoji/svg/1f1f0-1f1ec.svg',
    '/node_modules/@twemoji/svg/1f1f0-1f1ed.svg',
    '/node_modules/@twemoji/svg/1f1f0-1f1ee.svg',
    '/node_modules/@twemoji/svg/1f1f0-1f1f2.svg',
    '/node_modules/@twemoji/svg/1f1f0-1f1f3.svg',
    '/node_modules/@twemoji/svg/1f1f0-1f1f5.svg',
    '/node_modules/@twemoji/svg/1f1f0-1f1f7.svg',
    '/node_modules/@twemoji/svg/1f1f0-1f1fc.svg',
    '/node_modules/@twemoji/svg/1f1f0-1f1ff.svg',
    '/node_modules/@twemoji/svg/1f1f1-1f1e6.svg',
    '/node_modules/@twemoji/svg/1f1f1-1f1e7.svg',
    '/node_modules/@twemoji/svg/1f1f1-1f1e8.svg',
    '/node_modules/@twemoji/svg/1f1f1-1f1ee.svg',
    '/node_modules/@twemoji/svg/1f1f1-1f1f0.svg',
    '/node_modules/@twemoji/svg/1f1f1-1f1f7.svg',
    '/node_modules/@twemoji/svg/1f1f1-1f1f8.svg',
    '/node_modules/@twemoji/svg/1f1f1-1f1f9.svg',
    '/node_modules/@twemoji/svg/1f1f1-1f1fa.svg',
    '/node_modules/@twemoji/svg/1f1f1-1f1fb.svg',
    '/node_modules/@twemoji/svg/1f1f1-1f1fe.svg',
    '/node_modules/@twemoji/svg/1f1f2-1f1e6.svg',
    '/node_modules/@twemoji/svg/1f1f2-1f1e8.svg',
    '/node_modules/@twemoji/svg/1f1f2-1f1e9.svg',
    '/node_modules/@twemoji/svg/1f1f2-1f1ea.svg',
    '/node_modules/@twemoji/svg/1f1f2-1f1ec.svg',
    '/node_modules/@twemoji/svg/1f1f2-1f1ed.svg',
    '/node_modules/@twemoji/svg/1f1f2-1f1f0.svg',
    '/node_modules/@twemoji/svg/1f1f2-1f1f1.svg',
    '/node_modules/@twemoji/svg/1f1f2-1f1f2.svg',
    '/node_modules/@twemoji/svg/1f1f2-1f1f3.svg',
    '/node_modules/@twemoji/svg/1f1f2-1f1f7.svg',
    '/node_modules/@twemoji/svg/1f1f2-1f1f9.svg',
    '/node_modules/@twemoji/svg/1f1f2-1f1fa.svg',
    '/node_modules/@twemoji/svg/1f1f2-1f1fb.svg',
    '/node_modules/@twemoji/svg/1f1f2-1f1fc.svg',
    '/node_modules/@twemoji/svg/1f1f2-1f1fd.svg',
    '/node_modules/@twemoji/svg/1f1f2-1f1fe.svg',
    '/node_modules/@twemoji/svg/1f1f2-1f1ff.svg',
    '/node_modules/@twemoji/svg/1f1f3-1f1e6.svg',
    '/node_modules/@twemoji/svg/1f1f3-1f1ea.svg',
    '/node_modules/@twemoji/svg/1f1f3-1f1ec.svg',
    '/node_modules/@twemoji/svg/1f1f3-1f1ee.svg',
    '/node_modules/@twemoji/svg/1f1f3-1f1f1.svg',
    '/node_modules/@twemoji/svg/1f1f3-1f1f4.svg',
    '/node_modules/@twemoji/svg/1f1f3-1f1f5.svg',
    '/node_modules/@twemoji/svg/1f1f3-1f1f7.svg',
    '/node_modules/@twemoji/svg/1f1f3-1f1ff.svg',
    '/node_modules/@twemoji/svg/1f1f4-1f1f2.svg',
    '/node_modules/@twemoji/svg/1f1f5-1f1e6.svg',
    '/node_modules/@twemoji/svg/1f1f5-1f1ea.svg',
    '/node_modules/@twemoji/svg/1f1f5-1f1ec.svg',
    '/node_modules/@twemoji/svg/1f1f5-1f1ed.svg',
    '/node_modules/@twemoji/svg/1f1f5-1f1f0.svg',
    '/node_modules/@twemoji/svg/1f1f5-1f1f1.svg',
    '/node_modules/@twemoji/svg/1f1f5-1f1f8.svg',
    '/node_modules/@twemoji/svg/1f1f5-1f1f9.svg',
    '/node_modules/@twemoji/svg/1f1f5-1f1fc.svg',
    '/node_modules/@twemoji/svg/1f1f5-1f1fe.svg',
    '/node_modules/@twemoji/svg/1f1f6-1f1e6.svg',
    '/node_modules/@twemoji/svg/1f1f7-1f1f4.svg',
    '/node_modules/@twemoji/svg/1f1f7-1f1f8.svg',
    '/node_modules/@twemoji/svg/1f1f7-1f1fa.svg',
    '/node_modules/@twemoji/svg/1f1f7-1f1fc.svg',
    '/node_modules/@twemoji/svg/1f1f8-1f1e6.svg',
    '/node_modules/@twemoji/svg/1f1f8-1f1e7.svg',
    '/node_modules/@twemoji/svg/1f1f8-1f1e8.svg',
    '/node_modules/@twemoji/svg/1f1f8-1f1e9.svg',
    '/node_modules/@twemoji/svg/1f1f8-1f1ea.svg',
    '/node_modules/@twemoji/svg/1f1f8-1f1ec.svg',
    '/node_modules/@twemoji/svg/1f1f8-1f1ee.svg',
    '/node_modules/@twemoji/svg/1f1f8-1f1f0.svg',
    '/node_modules/@twemoji/svg/1f1f8-1f1f1.svg',
    '/node_modules/@twemoji/svg/1f1f8-1f1f2.svg',
    '/node_modules/@twemoji/svg/1f1f8-1f1f3.svg',
    '/node_modules/@twemoji/svg/1f1f8-1f1f4.svg',
    '/node_modules/@twemoji/svg/1f1f8-1f1f7.svg',
    '/node_modules/@twemoji/svg/1f1f8-1f1f8.svg',
    '/node_modules/@twemoji/svg/1f1f8-1f1f9.svg',
    '/node_modules/@twemoji/svg/1f1f8-1f1fb.svg',
    '/node_modules/@twemoji/svg/1f1f8-1f1fe.svg',
    '/node_modules/@twemoji/svg/1f1f8-1f1ff.svg',
    '/node_modules/@twemoji/svg/1f1f9-1f1e9.svg',
    '/node_modules/@twemoji/svg/1f1f9-1f1ec.svg',
    '/node_modules/@twemoji/svg/1f1f9-1f1ed.svg',
    '/node_modules/@twemoji/svg/1f1f9-1f1ef.svg',
    '/node_modules/@twemoji/svg/1f1f9-1f1f1.svg',
    '/node_modules/@twemoji/svg/1f1f9-1f1f2.svg',
    '/node_modules/@twemoji/svg/1f1f9-1f1f3.svg',
    '/node_modules/@twemoji/svg/1f1f9-1f1f4.svg',
    '/node_modules/@twemoji/svg/1f1f9-1f1f7.svg',
    '/node_modules/@twemoji/svg/1f1f9-1f1f9.svg',
    '/node_modules/@twemoji/svg/1f1f9-1f1fb.svg',
    '/node_modules/@twemoji/svg/1f1f9-1f1ff.svg',
    '/node_modules/@twemoji/svg/1f1fa-1f1e6.svg',
    '/node_modules/@twemoji/svg/1f1fa-1f1ec.svg',
    '/node_modules/@twemoji/svg/1f1fa-1f1f8.svg',
    '/node_modules/@twemoji/svg/1f1fa-1f1fe.svg',
    '/node_modules/@twemoji/svg/1f1fa-1f1ff.svg',
    '/node_modules/@twemoji/svg/1f1fb-1f1e6.svg',
    '/node_modules/@twemoji/svg/1f1fb-1f1e8.svg',
    '/node_modules/@twemoji/svg/1f1fb-1f1ea.svg',
    '/node_modules/@twemoji/svg/1f1fb-1f1f3.svg',
    '/node_modules/@twemoji/svg/1f1fb-1f1fa.svg',
    '/node_modules/@twemoji/svg/1f1fc-1f1f8.svg',
    '/node_modules/@twemoji/svg/1f1fe-1f1ea.svg',
    '/node_modules/@twemoji/svg/1f1ff-1f1e6.svg',
    '/node_modules/@twemoji/svg/1f1ff-1f1f2.svg',
    '/node_modules/@twemoji/svg/1f1ff-1f1fc.svg',
  ],
  { import: 'default', query: '?url' },
);

const flagAssetLoaders = Object.fromEntries(
  Object.entries(flagAssetModules).map(([path, loadAsset]) => [
    path.slice(path.lastIndexOf('/') + 1, -'.svg'.length),
    loadAsset,
  ]),
) as Record<string, FlagAssetLoader>;

const stateFlagAssetModules = import.meta.glob<string>('/src/assets/flags/br-states/br-*.svg', {
  import: 'default',
  query: '?url',
});

const stateFlagAssetLoaders = Object.fromEntries(
  Object.entries(stateFlagAssetModules).map(([path, loadAsset]) => [
    path.slice(path.lastIndexOf('/') + 1, -'.svg'.length).toUpperCase(),
    loadAsset,
  ]),
) as Record<string, FlagAssetLoader>;

const loadedAssetUrls = new Map<FlagAssetLoader, string>();
const pendingAssetLoads = new Map<FlagAssetLoader, Promise<string | null>>();
const failedAssetUrls = new Set<string>();

interface FlagEmojiProps {
  className?: string;
  emoji: string;
}

export function FlagEmoji({ className = '', emoji }: FlagEmojiProps) {
  const codePoint = twemoji.convert.toCodePoint(emoji).toLowerCase();
  const loader = stateFlagAssetLoaders[emoji.toUpperCase()] ?? flagAssetLoaders[codePoint] ?? null;
  const cachedAssetUrl = loader ? loadedAssetUrls.get(loader) : undefined;
  const availableCachedAssetUrl =
    cachedAssetUrl && !failedAssetUrls.has(cachedAssetUrl) ? cachedAssetUrl : null;
  const [assetState, setAssetState] = useState<FlagAssetState>(() => ({
    assetUrl: availableCachedAssetUrl,
    loader,
  }));
  const assetUrl = assetState.loader === loader ? assetState.assetUrl : availableCachedAssetUrl;

  useEffect(() => {
    if (!loader) return undefined;

    const cachedUrl = loadedAssetUrls.get(loader);
    if (cachedUrl) return undefined;

    let isActive = true;
    void loadFlagAsset(loader).then((loadedUrl) => {
      if (isActive && loadedUrl && !failedAssetUrls.has(loadedUrl)) {
        setAssetState({ assetUrl: loadedUrl, loader });
      }
    });

    return () => {
      isActive = false;
    };
  }, [loader]);

  if (!assetUrl) {
    return (
      <span aria-label={emoji} className={className} role="img">
        {emoji}
      </span>
    );
  }

  return (
    <img
      alt={emoji}
      className={`object-contain ${className}`}
      decoding="async"
      draggable={false}
      loading="lazy"
      onError={() => {
        failedAssetUrls.add(assetUrl);
        setAssetState({ assetUrl: null, loader });
      }}
      src={assetUrl}
    />
  );
}

interface FlagAssetState {
  assetUrl: string | null;
  loader: FlagAssetLoader | null;
}

function loadFlagAsset(loader: FlagAssetLoader) {
  const cachedUrl = loadedAssetUrls.get(loader);
  if (cachedUrl) return Promise.resolve(cachedUrl);

  const pendingLoad = pendingAssetLoads.get(loader);
  if (pendingLoad) return pendingLoad;

  const load = loader()
    .then((assetUrl) => {
      loadedAssetUrls.set(loader, assetUrl);
      return assetUrl;
    })
    .catch(() => null);
  pendingAssetLoads.set(loader, load);
  return load;
}
