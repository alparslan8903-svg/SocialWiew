import { useEffect, useRef, useState } from 'react';
import { BadgeCheck, ExternalLink, Play, RefreshCw, X } from 'lucide-react';
import { downloadCloudVideoBlobUrl } from '../lib/socialRepository';
import { formatDurationMs } from '../lib/mediaUtils';
import type { AppUpdateInfo, VideoPost } from '../types/social';

export function CreatorAvatar({
  name,
  avatarUrl,
  size = 44,
}: {
  name: string;
  avatarUrl?: string;
  size?: number;
}) {
  const [broken, setBroken] = useState(false);
  const initials =
    name
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((p) => p[0]?.toUpperCase() ?? '')
      .join('') || 'S';

  if (avatarUrl && !broken) {
    return (
      <img
        src={avatarUrl}
        alt={name}
        onError={() => setBroken(true)}
        style={{ width: size, height: size }}
        className="rounded-full object-cover shrink-0 border border-white/15"
      />
    );
  }

  return (
    <div
      style={{ width: size, height: size, fontSize: Math.max(12, Math.round(size * 0.36)) }}
      className="rounded-full shrink-0 flex items-center justify-center font-bold text-white bg-gradient-to-br from-[#ff3b6b] to-[#00e5c3]"
    >
      {initials}
    </div>
  );
}

export function VerifiedTickIcon({ size = 16 }: { size?: number }) {
  return (
    <BadgeCheck
      style={{ width: size, height: size }}
      className="text-sky-400 shrink-0 inline-block"
      aria-label="Onaylı Hesap"
    />
  );
}

export function VerifiedAdminBadge({ label }: { label: string }) {
  return (
    <span className="text-[11px] font-semibold text-sky-400">
      {label}
    </span>
  );
}

export function VideoPlayerSurface({
  video,
  isActive,
  isMuted,
}: {
  video: VideoPost;
  isActive: boolean;
  isMuted: boolean;
}) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [resolvedSrc, setResolvedSrc] = useState<string | null>(
    video.videoUrl?.trim() ? video.videoUrl.trim() : null
  );
  const [loadingCloud, setLoadingCloud] = useState(false);
  const [cloudError, setCloudError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    if (video.videoUrl?.trim()) {
      setResolvedSrc(video.videoUrl.trim());
      setCloudError(null);
      setLoadingCloud(false);
      return;
    }
    if (video.chunkCount <= 0) {
      setResolvedSrc(null);
      setCloudError('Bu video yalnızca mobil cihazda kayıtlı görünüyor.');
      return;
    }

    setLoadingCloud(true);
    setCloudError(null);
    downloadCloudVideoBlobUrl(video.id, video.chunkCount)
      .then((url) => {
        if (!cancelled) {
          setResolvedSrc(url);
          setLoadingCloud(false);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setCloudError(err instanceof Error ? err.message : 'Video yüklenemedi.');
          setLoadingCloud(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [video.id, video.videoUrl, video.chunkCount]);

  useEffect(() => {
    const el = videoRef.current;
    if (!el) return;
    el.muted = isMuted;
    if (isActive && resolvedSrc) {
      el.play().catch(() => {
        // Autoplay with sound may be blocked until user interaction
      });
    } else {
      el.pause();
    }
  }, [isActive, isMuted, resolvedSrc]);

  return (
    <div className="relative w-full h-full bg-[#06070b] flex items-center justify-center overflow-hidden">
      {video.thumbnailData && !resolvedSrc && (
        <img
          src={video.thumbnailData}
          alt={video.caption || 'Video önizleme'}
          className="absolute inset-0 w-full h-full object-cover opacity-80"
        />
      )}

      {resolvedSrc && (
        <video
          ref={videoRef}
          src={resolvedSrc}
          poster={video.thumbnailData || undefined}
          loop
          playsInline
          muted={isMuted}
          controls
          className="w-full h-full object-contain"
        />
      )}

      {loadingCloud && (
        <div className="absolute inset-0 bg-black/50 flex flex-col items-center justify-center gap-2 text-white text-xs">
          <RefreshCw className="w-6 h-6 animate-spin text-[#00e5c3]" />
          <span>Bulut videosu hazırlanıyor...</span>
        </div>
      )}

      {cloudError && !loadingCloud && (
        <div className="relative z-10 px-6 text-center text-xs text-white/80">
          {cloudError}
        </div>
      )}
    </div>
  );
}

export function VideoGridCard({
  video,
  onClick,
}: {
  video: VideoPost;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="group relative aspect-[9/14] w-full rounded-2xl overflow-hidden bg-[var(--sw-surface-variant)] border border-[var(--sw-outline)] text-left cursor-pointer"
    >
      {video.thumbnailData ? (
        <img
          src={video.thumbnailData}
          alt={video.caption || video.authorUsername}
          className="w-full h-full object-cover group-hover:scale-105 transition duration-300"
        />
      ) : (
        <div className="w-full h-full flex items-center justify-center text-[var(--sw-primary)]">
          <Play className="w-10 h-10" />
        </div>
      )}
      <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/15 to-transparent" />
      <div className="absolute top-2.5 right-2.5 text-[11px] font-semibold text-white/90 bg-black/60 px-2 py-0.5 rounded-md tabular-nums">
        {formatDurationMs(video.durationMs)}
      </div>
      <div className="absolute bottom-2.5 left-2.5 right-2.5">
        <div className="text-xs font-bold text-white line-clamp-2">
          {video.caption || `@${video.authorUsername}`}
        </div>
        <div className="text-[11px] text-white/75 mt-1 tabular-nums">
          {video.likesCount} beğeni · {video.commentsCount} yorum
        </div>
      </div>
    </button>
  );
}

export function GitHubUpdateBanner({
  info,
  onOpenDetails,
  onDismiss,
}: {
  info: AppUpdateInfo | null;
  onOpenDetails: () => void;
  onDismiss: () => void;
}) {
  if (!info || !info.isNewerAvailable) return null;
  return (
    <div className="mx-4 mt-3 rounded-2xl bg-[var(--sw-surface)] border border-[var(--sw-secondary)]/50 px-4 py-3 flex items-center justify-between gap-3 shadow-lg">
      <div className="min-w-0">
        <div className="text-xs font-bold text-[var(--sw-secondary)]">
          Yeni Sürüm Mevcut: {info.tagName}
        </div>
        <div className="text-xs text-[var(--sw-text-secondary)] truncate">
          {info.title}
        </div>
      </div>
      <div className="flex items-center gap-2 shrink-0">
        <button
          type="button"
          onClick={onOpenDetails}
          className="text-xs font-bold px-3 py-1.5 rounded-xl bg-[var(--sw-secondary)] text-black cursor-pointer"
        >
          İncele
        </button>
        <button
          type="button"
          onClick={onDismiss}
          className="p-1 text-[var(--sw-text-secondary)] hover:text-[var(--sw-text-primary)] cursor-pointer"
          aria-label="Kapat"
        >
          <X className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}

export function GitHubUpdateModal({
  info,
  onClose,
}: {
  info: AppUpdateInfo | null;
  onClose: () => void;
}) {
  if (!info) return null;
  return (
    <div className="fixed inset-0 z-50 bg-black/70 flex items-center justify-center p-4">
      <div className="w-full max-w-md rounded-3xl bg-[var(--sw-surface)] border border-[var(--sw-outline)] p-6 space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="font-display text-lg font-bold">{info.title}</h3>
          <button type="button" onClick={onClose} className="p-1 cursor-pointer">
            <X className="w-5 h-5" />
          </button>
        </div>
        <div className="text-xs text-[var(--sw-text-secondary)]">
          Depo: {info.repoSlug} · Sürüm: {info.tagName} (Mevcut: {info.currentVersion})
        </div>
        <p className="text-sm whitespace-pre-wrap max-h-60 overflow-y-auto text-[var(--sw-text-primary)]">
          {info.releaseNotes}
        </p>
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-xl border border-[var(--sw-outline)] text-xs font-semibold cursor-pointer"
          >
            Kapat
          </button>
          <a
            href={info.htmlUrl}
            target="_blank"
            rel="noreferrer"
            className="px-4 py-2 rounded-xl bg-[var(--sw-primary)] text-white text-xs font-bold inline-flex items-center gap-1.5"
          >
            GitHub&apos;da Aç <ExternalLink className="w-3.5 h-3.5" />
          </a>
        </div>
      </div>
    </div>
  );
}
