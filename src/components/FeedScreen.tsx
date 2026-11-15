import { useMemo, useState } from 'react';
import {
  ChevronDown,
  ChevronUp,
  Heart,
  MessageCircle,
  Search,
  Share2,
  Upload,
  Volume2,
  VolumeX,
} from 'lucide-react';
import { isAdminUsername, isVerifiedUsername } from '../lib/socialRepository';
import type { VideoPost } from '../types/social';
import {
  CreatorAvatar,
  VerifiedAdminBadge,
  VerifiedTickIcon,
  VideoPlayerSurface,
} from './CommonUI';

export function FeedScreen({
  videos,
  loading,
  error,
  currentUserId,
  verifiedUserIds,
  followingIds,
  likedVideoIds,
  onToggleLike,
  onToggleFollow,
  onOpenComments,
  onOpenCreatorProfile,
  onNavigateToUpload,
  onNavigateToDiscover,
  onNavigateToSearch,
  onShareFeedback,
}: {
  videos: VideoPost[];
  loading: boolean;
  error: string | null;
  currentUserId: string;
  verifiedUserIds: Set<string>;
  followingIds: Set<string>;
  likedVideoIds: Set<string>;
  onToggleLike: (video: VideoPost) => void;
  onToggleFollow: (userId: string) => void;
  onOpenComments: (video: VideoPost) => void;
  onOpenCreatorProfile: (userId: string) => void;
  onNavigateToUpload: () => void;
  onNavigateToDiscover: () => void;
  onNavigateToSearch: () => void;
  onShareFeedback: (msg: string) => void;
}) {
  const [tab, setTab] = useState<'forYou' | 'following'>('forYou');
  const [index, setIndex] = useState(0);
  const [isMuted, setIsMuted] = useState(false);

  const filteredVideos = useMemo(() => {
    if (tab === 'following') {
      return videos.filter((v) => followingIds.has(v.userId));
    }
    return videos;
  }, [videos, tab, followingIds]);

  const safeIndex = filteredVideos.length > 0 ? Math.min(index, filteredVideos.length - 1) : 0;
  const activeVideo = filteredVideos[safeIndex];

  const handleShare = async (video: VideoPost) => {
    const shareText = [
      `SocialWiew'de @${video.authorUsername} videosunu izle!`,
      video.caption,
      video.videoUrl,
    ]
      .filter(Boolean)
      .join('\n');

    if (navigator.share) {
      try {
        await navigator.share({ title: 'SocialWiew', text: shareText });
        return;
      } catch {
        // Fallback to clipboard
      }
    }
    try {
      await navigator.clipboard.writeText(shareText);
      onShareFeedback('Video bağlantısı panoya kopyalandı.');
    } catch {
      onShareFeedback('Paylaşım hazır.');
    }
  };

  return (
    <div className="relative h-full min-h-[calc(100vh-4rem)] md:min-h-screen bg-[#06070b] text-white flex flex-col">
      <div className="flex items-center justify-between px-4 py-3 z-20">
        <button
          type="button"
          onClick={onNavigateToSearch}
          className="w-10 h-10 rounded-full bg-white/10 hover:bg-white/15 flex items-center justify-center cursor-pointer"
          aria-label="Ara"
        >
          <Search className="w-4 h-4" />
        </button>

        <div className="inline-flex rounded-2xl bg-white/10 p-1">
          <button
            type="button"
            onClick={() => {
              setTab('forYou');
              setIndex(0);
            }}
            className={`px-4 py-1.5 rounded-xl text-xs font-bold cursor-pointer transition ${
              tab === 'forYou' ? 'bg-[var(--sw-primary)] text-white' : 'text-white/75'
            }`}
          >
            Senin İçin
          </button>
          <button
            type="button"
            onClick={() => {
              setTab('following');
              setIndex(0);
            }}
            className={`px-4 py-1.5 rounded-xl text-xs font-bold cursor-pointer transition ${
              tab === 'following' ? 'bg-[var(--sw-primary)] text-white' : 'text-white/75'
            }`}
          >
            Takip
          </button>
        </div>

        <button
          type="button"
          onClick={() => setIsMuted((m) => !m)}
          className="w-10 h-10 rounded-full bg-white/10 hover:bg-white/15 flex items-center justify-center cursor-pointer"
          aria-label={isMuted ? 'Sesi aç' : 'Sesi kapat'}
        >
          {isMuted ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4" />}
        </button>
      </div>

      {loading ? (
        <div className="flex-1 flex items-center justify-center text-sm text-white/70">
          Videolar yükleniyor...
        </div>
      ) : error ? (
        <div className="flex-1 flex items-center justify-center p-6 text-center text-sm text-red-400">
          {error}
        </div>
      ) : !activeVideo ? (
        <div className="flex-1 flex flex-col items-center justify-center p-8 text-center">
          <img
            src="/assets/img_empty_feed.jpg"
            alt="Boş video akışı"
            className="w-44 h-44 rounded-3xl object-cover mb-5"
          />
          <h3 className="font-display text-xl font-bold">
            {tab === 'following' ? 'Henüz kimseyi takip etmiyorsun' : 'Akış henüz sessiz'}
          </h3>
          <p className="text-sm text-white/70 max-w-sm mt-2">
            {tab === 'following'
              ? 'Keşfet sekmesinden yeni içerik üreticilerini takip ederek videolarını burada görebilirsin.'
              : "Topluluğun ilk kısa videosunu paylaşarak SocialWiew'i başlat!"}
          </p>
          <button
            type="button"
            onClick={tab === 'following' ? onNavigateToDiscover : onNavigateToUpload}
            className="mt-5 px-5 py-3 rounded-2xl bg-[var(--sw-primary)] text-white text-sm font-bold inline-flex items-center gap-2 cursor-pointer"
          >
            <Upload className="w-4 h-4" />
            {tab === 'following' ? 'İçerik Üreticilerini Keşfet' : 'İlk Videoyu Yükle'}
          </button>
        </div>
      ) : (
        <div className="flex-1 flex items-center justify-center px-4 pb-6">
          <div className="w-full max-w-5xl grid md:grid-cols-[minmax(280px,410px)_1fr] gap-6 items-center">
            <div className="relative aspect-[9/16] max-h-[76vh] w-full mx-auto rounded-3xl overflow-hidden border border-white/15 bg-black">
              <VideoPlayerSurface video={activeVideo} isActive isMuted={isMuted} />
            </div>

            <div className="rounded-3xl bg-[var(--sw-surface)] text-[var(--sw-text-primary)] border border-[var(--sw-outline)] p-6 space-y-4">
              <div className="flex items-center justify-between gap-3">
                <button
                  type="button"
                  onClick={() => onOpenCreatorProfile(activeVideo.userId)}
                  className="flex items-center gap-3 text-left min-w-0 cursor-pointer"
                >
                  <CreatorAvatar
                    name={activeVideo.authorName}
                    avatarUrl={activeVideo.authorAvatarUrl}
                    size={48}
                  />
                  <div className="min-w-0">
                    <div className="flex items-center gap-1.5">
                      <span className="font-bold truncate">
                        {activeVideo.authorName || activeVideo.authorUsername}
                      </span>
                      {(activeVideo.authorVerified ||
                        verifiedUserIds.has(activeVideo.userId) ||
                        isVerifiedUsername(activeVideo.authorUsername)) && <VerifiedTickIcon />}
                    </div>
                    <div className="text-xs text-[var(--sw-secondary)] truncate">
                      @{activeVideo.authorUsername}
                    </div>
                    {(activeVideo.authorVerified ||
                      verifiedUserIds.has(activeVideo.userId) ||
                      isVerifiedUsername(activeVideo.authorUsername)) && (
                      <VerifiedAdminBadge
                        label={isAdminUsername(activeVideo.authorUsername) ? 'Admin' : 'Doğrulanmış'}
                      />
                    )}
                  </div>
                </button>

                {activeVideo.userId !== currentUserId && (
                  <button
                    type="button"
                    onClick={() => onToggleFollow(activeVideo.userId)}
                    className={`px-4 py-2 rounded-xl text-xs font-bold cursor-pointer shrink-0 ${
                      followingIds.has(activeVideo.userId)
                        ? 'bg-[var(--sw-surface-variant)] text-[var(--sw-text-primary)]'
                        : 'bg-[var(--sw-primary)] text-white'
                    }`}
                  >
                    {followingIds.has(activeVideo.userId) ? 'Takip Ediliyor' : 'Takip Et'}
                  </button>
                )}
              </div>

              {activeVideo.caption && <p className="text-sm leading-relaxed">{activeVideo.caption}</p>}

              {activeVideo.tags.length > 0 && (
                <div className="text-xs font-semibold text-[var(--sw-secondary)]">
                  {activeVideo.tags.map((t) => `#${t}`).join('  ')}
                </div>
              )}

              <div className="grid grid-cols-2 gap-3">
                <button
                  type="button"
                  onClick={() => onToggleLike(activeVideo)}
                  className={`h-11 rounded-xl font-bold text-xs flex items-center justify-center gap-2 cursor-pointer border ${
                    likedVideoIds.has(activeVideo.id)
                      ? 'bg-[var(--sw-primary-container)] text-[var(--sw-primary)] border-[var(--sw-primary)]/40'
                      : 'bg-[var(--sw-surface-variant)] border-transparent'
                  }`}
                >
                  <Heart
                    className={`w-4 h-4 ${likedVideoIds.has(activeVideo.id) ? 'fill-current' : ''}`}
                  />
                  <span className="tabular-nums">{activeVideo.likesCount}</span>
                </button>

                <button
                  type="button"
                  onClick={() => handleShare(activeVideo)}
                  className="h-11 rounded-xl bg-[var(--sw-surface-variant)] font-bold text-xs flex items-center justify-center gap-2 cursor-pointer"
                >
                  <Share2 className="w-4 h-4" />
                  Paylaş
                </button>
              </div>

              <button
                type="button"
                onClick={() => onOpenComments(activeVideo)}
                className="w-full h-12 rounded-2xl bg-[var(--sw-secondary)] text-black font-bold text-sm flex items-center justify-center gap-2 cursor-pointer tabular-nums"
              >
                <MessageCircle className="w-4 h-4" />
                Yorumlar &amp; Tenor GIF ({activeVideo.commentsCount})
              </button>

              <div className="flex items-center justify-between pt-2 border-t border-[var(--sw-outline)] text-xs text-[var(--sw-text-secondary)]">
                <span className="tabular-nums">
                  {safeIndex + 1} / {filteredVideos.length}
                </span>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    disabled={safeIndex <= 0}
                    onClick={() => setIndex((i) => Math.max(0, i - 1))}
                    className="p-2 rounded-xl bg-[var(--sw-surface-variant)] disabled:opacity-40 cursor-pointer"
                    aria-label="Önceki video"
                  >
                    <ChevronUp className="w-4 h-4" />
                  </button>
                  <button
                    type="button"
                    disabled={safeIndex >= filteredVideos.length - 1}
                    onClick={() => setIndex((i) => Math.min(filteredVideos.length - 1, i + 1))}
                    className="p-2 rounded-xl bg-[var(--sw-surface-variant)] disabled:opacity-40 cursor-pointer"
                    aria-label="Sonraki video"
                  >
                    <ChevronDown className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
