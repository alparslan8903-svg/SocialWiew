import type { AppUpdateInfo, TenorGifItem } from '../types/social';

export interface ExtractedVideoMeta {
  thumbnailData: string | null;
  durationMs: number;
}

export async function extractVideoMetaFromFile(file: File): Promise<ExtractedVideoMeta> {
  return new Promise((resolve) => {
    const objectUrl = URL.createObjectURL(file);
    const video = document.createElement('video');
    video.preload = 'metadata';
    video.muted = true;
    video.playsInline = true;
    video.src = objectUrl;

    let settled = false;
    const finish = (result: ExtractedVideoMeta) => {
      if (settled) return;
      settled = true;
      URL.revokeObjectURL(objectUrl);
      resolve(result);
    };

    const timeout = window.setTimeout(() => {
      finish({ thumbnailData: null, durationMs: 0 });
    }, 6000);

    video.onloadeddata = () => {
      const durationMs = Number.isFinite(video.duration)
        ? Math.max(0, Math.round(video.duration * 1000))
        : 0;
      const seekTime = Number.isFinite(video.duration) && video.duration > 0.3 ? 0.2 : 0;
      try {
        video.currentTime = seekTime;
      } catch {
        window.clearTimeout(timeout);
        finish({ thumbnailData: null, durationMs });
      }
    };

    video.onseeked = () => {
      window.clearTimeout(timeout);
      const durationMs = Number.isFinite(video.duration)
        ? Math.max(0, Math.round(video.duration * 1000))
        : 0;
      try {
        const vw = video.videoWidth || 360;
        const vh = video.videoHeight || 640;
        const scale = Math.min(1, 360 / Math.max(vw, vh));
        const canvas = document.createElement('canvas');
        canvas.width = Math.max(1, Math.round(vw * scale));
        canvas.height = Math.max(1, Math.round(vh * scale));
        const ctx = canvas.getContext('2d');
        if (ctx) {
          ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
          const dataUrl = canvas.toDataURL('image/jpeg', 0.65);
          finish({
            thumbnailData: dataUrl.length <= 150_000 ? dataUrl : null,
            durationMs,
          });
          return;
        }
      } catch {
        // Ignore canvas taint/decode failures
      }
      finish({ thumbnailData: null, durationMs });
    };

    video.onerror = () => {
      window.clearTimeout(timeout);
      finish({ thumbnailData: null, durationMs: 0 });
    };
  });
}

export function formatDurationMs(durationMs: number): string {
  if (!durationMs || durationMs <= 0) return '0:00';
  const totalSec = Math.floor(durationMs / 1000);
  const mins = Math.floor(totalSec / 60);
  const secs = totalSec % 60;
  return `${mins}:${secs.toString().padStart(2, '0')}`;
}

export async function fetchTenorGifs(query: string): Promise<TenorGifItem[]> {
  const res = await fetch(`/api/tenor?q=${encodeURIComponent(query)}`);
  const body = (await res.json()) as { gifs?: TenorGifItem[]; error?: string };
  if (!res.ok) {
    throw new Error(body.error || "Tenor GIF'leri yüklenemedi.");
  }
  return body.gifs ?? [];
}

export async function fetchGitHubRelease(
  repo: string,
  currentVersion = '1.0'
): Promise<AppUpdateInfo> {
  const res = await fetch(
    `/api/github/releases?repo=${encodeURIComponent(repo)}&currentVersion=${encodeURIComponent(currentVersion)}`
  );
  const body = (await res.json()) as AppUpdateInfo & { error?: string };
  if (!res.ok) {
    throw new Error(body.error || 'GitHub güncellemeleri alınamadı.');
  }
  return body;
}
