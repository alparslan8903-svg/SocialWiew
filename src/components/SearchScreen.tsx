import { useMemo, useState } from 'react';
import { Clock, Search } from 'lucide-react';
import { isVerifiedUsername } from '../lib/socialRepository';
import type { UserPublic, VideoPost } from '../types/social';
import { CreatorAvatar, VerifiedTickIcon, VideoGridCard } from './CommonUI';

const RECENT_KEY = 'socialwiew_recent_searches';

export function SearchScreen({
  creators,
  videos,
  currentUserId,
  followingIds,
  onToggleFollow,
  onOpenCreatorProfile,
  onOpenVideoPlayer,
}: {
  creators: UserPublic[];
  videos: VideoPost[];
  currentUserId: string;
  followingIds: Set<string>;
  onToggleFollow: (userId: string) => void;
  onOpenCreatorProfile: (userId: string) => void;
  onOpenVideoPlayer: (video: VideoPost) => void;
}) {
  const [query, setQuery] = useState('');
  const [scope, setScope] = useState<'all' | 'creators' | 'videos'>('all');
  const [recent, setRecent] = useState<string[]>(() => {
    try {
      const raw = localStorage.getItem(RECENT_KEY);
      return raw ? (JSON.parse(raw) as string[]) : [];
    } catch {
      return [];
    }
  });

  const saveRecent = (term: string) => {
    const clean = term.trim();
    if (!clean) return;
    const next = [clean, ...recent.filter((r) => r.toLowerCase() !== clean.toLowerCase())].slice(0, 8);
    setRecent(next);
    try {
      localStorage.setItem(RECENT_KEY, JSON.stringify(next));
    } catch {
      // Ignore storage errors
    }
  };

  const trendingTags = useMemo(() => {
    const counts = new Map<string, number>();
    for (const v of videos) {
      for (const tag of v.tags) {
        if (tag) counts.set(tag, (counts.get(tag) ?? 0) + 1);
      }
    }
    return Array.from(counts.entries())
      .sort((a, b) => b[1] - a[1])
      .slice(0, 10)
      .map(([tag]) => tag);
  }, [videos]);

  const matchingCreators = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return creators;
    return creators.filter(
      (c) =>
        c.displayName.toLowerCase().includes(q) ||
        c.username.toLowerCase().includes(q) ||
        c.bio.toLowerCase().includes(q)
    );
  }, [creators, query]);

  const matchingVideos = useMemo(() => {
    const q = query.trim().toLowerCase().replace(/^#/, '');
    const list = !q
      ? [...videos].sort((a, b) => b.likesCount - a.likesCount)
      : videos.filter(
          (v) =>
            v.caption.toLowerCase().includes(q) ||
            v.authorName.toLowerCase().includes(q) ||
            v.authorUsername.toLowerCase().includes(q) ||
            v.tags.some((t) => t.toLowerCase().includes(q))
        );
    return list;
  }, [videos, query]);

  return (
    <div className="p-6 max-w-6xl mx-auto space-y-6">
      <div>
        <h2 className="font-display text-2xl font-bold">Ara ve Keşfet</h2>
        <div className="mt-3 relative">
          <Search className="w-4 h-4 text-[var(--sw-text-secondary)] absolute left-4 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') saveRecent(query);
            }}
            placeholder="Üretici, @kullanıcı veya #etiket ara..."
            className="w-full rounded-2xl bg-[var(--sw-surface)] border border-[var(--sw-outline)] pl-11 pr-4 py-3 text-sm focus:outline-none focus:border-[var(--sw-primary)]"
          />
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {(
          [
            ['all', 'Tümü'],
            ['creators', 'Üreticiler'],
            ['videos', 'Trend Videolar'],
          ] as const
        ).map(([key, label]) => (
          <button
            key={key}
            type="button"
            onClick={() => setScope(key)}
            className={`px-4 py-1.5 rounded-xl text-xs font-bold cursor-pointer ${
              scope === key
                ? 'bg-[var(--sw-primary)] text-white'
                : 'bg-[var(--sw-surface)] border border-[var(--sw-outline)]'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {trendingTags.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {trendingTags.map((tag) => (
            <button
              key={tag}
              type="button"
              onClick={() => {
                setQuery(tag);
                saveRecent(tag);
              }}
              className="px-3 py-1.5 rounded-xl bg-[var(--sw-surface-variant)] text-xs font-semibold text-[var(--sw-secondary)] cursor-pointer"
            >
              #{tag}
            </button>
          ))}
        </div>
      )}

      {!query && recent.length > 0 && (
        <div className="rounded-2xl bg-[var(--sw-surface)] border border-[var(--sw-outline)] p-4 space-y-2">
          <div className="flex items-center justify-between text-xs font-bold">
            <span>Son Aramalar</span>
            <button
              type="button"
              onClick={() => {
                setRecent([]);
                localStorage.removeItem(RECENT_KEY);
              }}
              className="text-[var(--sw-primary)] cursor-pointer"
            >
              Temizle
            </button>
          </div>
          <div className="flex flex-wrap gap-2">
            {recent.map((item) => (
              <button
                key={item}
                type="button"
                onClick={() => setQuery(item)}
                className="inline-flex items-center gap-1.5 px-3 py-1 rounded-xl bg-[var(--sw-surface-variant)] text-xs cursor-pointer"
              >
                <Clock className="w-3 h-3" />
                {item}
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="grid lg:grid-cols-12 gap-6">
        {(scope === 'all' || scope === 'creators') && (
          <section className={scope === 'all' ? 'lg:col-span-5 space-y-3' : 'lg:col-span-12 space-y-3'}>
            <h3 className="font-display text-base font-bold">Üreticiler</h3>
            <div className="space-y-2.5">
              {matchingCreators.map((creator) => {
                const verified = creator.isVerified || isVerifiedUsername(creator.username);
                const following = followingIds.has(creator.userId);
                return (
                  <div
                    key={creator.userId}
                    className="rounded-2xl bg-[var(--sw-surface)] border border-[var(--sw-outline)] p-3.5 flex items-center justify-between gap-3"
                  >
                    <button
                      type="button"
                      onClick={() => onOpenCreatorProfile(creator.userId)}
                      className="flex items-center gap-3 text-left min-w-0 cursor-pointer"
                    >
                      <CreatorAvatar name={creator.displayName} avatarUrl={creator.avatarUrl} size={42} />
                      <div className="min-w-0">
                        <div className="flex items-center gap-1">
                          <span className="text-sm font-bold truncate">{creator.displayName}</span>
                          {verified && <VerifiedTickIcon size={14} />}
                        </div>
                        <div className="text-xs text-[var(--sw-text-secondary)] truncate">
                          @{creator.username} · {creator.followersCount} takipçi
                        </div>
                      </div>
                    </button>
                    {creator.userId !== currentUserId && (
                      <button
                        type="button"
                        onClick={() => onToggleFollow(creator.userId)}
                        className={`px-3 py-1.5 rounded-xl text-xs font-bold shrink-0 cursor-pointer ${
                          following
                            ? 'bg-[var(--sw-surface-variant)]'
                            : 'bg-[var(--sw-primary)] text-white'
                        }`}
                      >
                        {following ? 'Takip Ediliyor' : 'Takip Et'}
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          </section>
        )}

        {(scope === 'all' || scope === 'videos') && (
          <section className={scope === 'all' ? 'lg:col-span-7 space-y-3' : 'lg:col-span-12 space-y-3'}>
            <h3 className="font-display text-base font-bold">Trend Videolar</h3>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
              {matchingVideos.map((video) => (
                <VideoGridCard key={video.id} video={video} onClick={() => onOpenVideoPlayer(video)} />
              ))}
            </div>
          </section>
        )}
      </div>
    </div>
  );
}
