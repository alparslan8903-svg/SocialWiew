import { useMemo, useState } from 'react';
import { Search } from 'lucide-react';
import { isVerifiedUsername } from '../lib/socialRepository';
import type { UserPublic, VideoPost } from '../types/social';
import { CreatorAvatar, VerifiedTickIcon, VideoGridCard } from './CommonUI';

export function DiscoverScreen({
  creators,
  videos,
  currentUserId,
  followingIds,
  onToggleFollow,
  onOpenCreatorProfile,
  onOpenVideoPlayer,
  onNavigateToSearch,
}: {
  creators: UserPublic[];
  videos: VideoPost[];
  currentUserId: string;
  followingIds: Set<string>;
  onToggleFollow: (userId: string) => void;
  onOpenCreatorProfile: (userId: string) => void;
  onOpenVideoPlayer: (video: VideoPost) => void;
  onNavigateToSearch: () => void;
}) {
  const [filter, setFilter] = useState('');

  const filteredCreators = useMemo(() => {
    const q = filter.trim().toLowerCase();
    if (!q) return creators;
    return creators.filter(
      (c) =>
        c.displayName.toLowerCase().includes(q) ||
        c.username.toLowerCase().includes(q)
    );
  }, [creators, filter]);

  const filteredVideos = useMemo(() => {
    const q = filter.trim().toLowerCase();
    if (!q) return videos;
    return videos.filter(
      (v) =>
        v.caption.toLowerCase().includes(q) ||
        v.authorUsername.toLowerCase().includes(q) ||
        v.tags.some((t) => t.toLowerCase().includes(q))
    );
  }, [videos, filter]);

  return (
    <div className="p-6 max-w-6xl mx-auto space-y-8">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="font-display text-2xl font-bold">Keşfet</h2>
          <p className="text-sm text-[var(--sw-text-secondary)]">
            Yeni içerik üreticileri ve topluluk videolarını keşfet
          </p>
        </div>
        <div className="flex items-center gap-2">
          <input
            type="text"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            placeholder="Keşfet içinde filtrele..."
            className="rounded-2xl bg-[var(--sw-surface)] border border-[var(--sw-outline)] px-4 py-2 text-sm focus:outline-none focus:border-[var(--sw-primary)]"
          />
          <button
            type="button"
            onClick={onNavigateToSearch}
            className="p-2.5 rounded-2xl bg-[var(--sw-surface)] border border-[var(--sw-outline)] cursor-pointer"
            aria-label="Gelişmiş Arama"
          >
            <Search className="w-4 h-4" />
          </button>
        </div>
      </div>

      <section className="space-y-3">
        <h3 className="font-display text-base font-bold">Yeni İçerik Üreticileri</h3>
        <div className="flex gap-3 overflow-x-auto pb-2">
          {filteredCreators.map((creator) => {
            const verified = creator.isVerified || isVerifiedUsername(creator.username);
            const isFollowing = followingIds.has(creator.userId);
            return (
              <div
                key={creator.userId}
                className="w-44 shrink-0 rounded-2xl bg-[var(--sw-surface)] border border-[var(--sw-outline)] p-4 flex flex-col items-center text-center gap-2"
              >
                <button
                  type="button"
                  onClick={() => onOpenCreatorProfile(creator.userId)}
                  className="flex flex-col items-center gap-1.5 cursor-pointer"
                >
                  <CreatorAvatar name={creator.displayName} avatarUrl={creator.avatarUrl} size={52} />
                  <div className="flex items-center gap-1 max-w-full">
                    <span className="text-sm font-bold truncate">{creator.displayName}</span>
                    {verified && <VerifiedTickIcon size={14} />}
                  </div>
                  <span className="text-xs text-[var(--sw-text-secondary)] truncate">
                    @{creator.username} · {creator.followersCount} takipçi
                  </span>
                </button>
                {creator.userId !== currentUserId && (
                  <button
                    type="button"
                    onClick={() => onToggleFollow(creator.userId)}
                    className={`w-full py-1.5 rounded-xl text-xs font-bold cursor-pointer mt-1 ${
                      isFollowing
                        ? 'bg-[var(--sw-surface-variant)] text-[var(--sw-text-primary)]'
                        : 'bg-[var(--sw-primary)] text-white'
                    }`}
                  >
                    {isFollowing ? 'Takip Ediliyor' : 'Takip Et'}
                  </button>
                )}
              </div>
            );
          })}
        </div>
      </section>

      <section className="space-y-3">
        <h3 className="font-display text-base font-bold">Topluluk Videoları</h3>
        {filteredVideos.length === 0 ? (
          <div className="rounded-2xl bg-[var(--sw-surface)] border border-[var(--sw-outline)] p-8 text-center text-sm text-[var(--sw-text-secondary)]">
            Gösterilecek video bulunamadı.
          </div>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
            {filteredVideos.map((video) => (
              <VideoGridCard key={video.id} video={video} onClick={() => onOpenVideoPlayer(video)} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
