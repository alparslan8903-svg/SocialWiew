import { useState, type ChangeEvent } from 'react';
import { Film, Upload } from 'lucide-react';
import { extractVideoMetaFromFile, formatDurationMs } from '../lib/mediaUtils';
import { publishVideo } from '../lib/socialRepository';
import type { UserPublic } from '../types/social';

export function UploadScreen({
  author,
  onPublished,
  onNotify,
}: {
  author: UserPublic | null;
  onPublished: () => void;
  onNotify: (msg: string) => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [thumbnailData, setThumbnailData] = useState<string | null>(null);
  const [durationMs, setDurationMs] = useState(0);
  const [videoUrl, setVideoUrl] = useState('');
  const [caption, setCaption] = useState('');
  const [tagsInput, setTagsInput] = useState('');
  const [isPublishing, setIsPublishing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleFileChange = async (e: ChangeEvent<HTMLInputElement>) => {
    const picked = e.target.files?.[0] ?? null;
    setError(null);
    if (!picked) return;
    if (picked.size > 15_500_000) {
      setError('Video boyutu en fazla 15 MB olabilir. Lütfen daha kısa bir video seçin.');
      return;
    }
    setFile(picked);
    const meta = await extractVideoMetaFromFile(picked);
    setThumbnailData(meta.thumbnailData);
    setDurationMs(meta.durationMs);
  };

  const handleSubmit = async () => {
    if (!author) {
      setError('Profiliniz henüz yüklenmedi.');
      return;
    }
    setError(null);
    setIsPublishing(true);
    try {
      const tags = tagsInput
        .split(',')
        .map((t) => t.trim().replace(/^#/, ''))
        .filter(Boolean)
        .slice(0, 5);
      await publishVideo({
        author,
        caption,
        videoUrl,
        file,
        thumbnailData,
        durationMs,
        tags,
      });
      setFile(null);
      setThumbnailData(null);
      setDurationMs(0);
      setVideoUrl('');
      setCaption('');
      setTagsInput('');
      onNotify('Video başarıyla paylaşıldı!');
      onPublished();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Video paylaşılamadı.');
    } finally {
      setIsPublishing(false);
    }
  };

  return (
    <div className="p-6 max-w-3xl mx-auto space-y-6">
      <div>
        <h2 className="font-display text-2xl font-bold">Yeni Video Paylaş</h2>
        <p className="text-sm text-[var(--sw-text-secondary)]">
          Cihazından 15 MB&apos;a kadar kısa video yükle veya doğrudan HTTPS video bağlantısı paylaş.
        </p>
      </div>

      <div className="rounded-3xl bg-[var(--sw-surface)] border border-[var(--sw-outline)] p-6 space-y-5">
        <label className="flex flex-col items-center justify-center rounded-2xl border-2 border-dashed border-[var(--sw-outline)] hover:border-[var(--sw-primary)] p-6 cursor-pointer transition text-center gap-2">
          <Film className="w-8 h-8 text-[var(--sw-primary)]" />
          <span className="text-sm font-bold">
            {file ? `${file.name} (${formatDurationMs(durationMs)})` : 'Cihazdan Video Seç (Maks. 15 MB)'}
          </span>
          <span className="text-xs text-[var(--sw-text-secondary)]">MP4 veya WebM önerilir</span>
          <input type="file" accept="video/*" onChange={handleFileChange} className="hidden" />
        </label>

        {thumbnailData && (
          <img
            src={thumbnailData}
            alt="Video küçük resmi"
            className="w-36 h-48 object-cover rounded-2xl border border-[var(--sw-outline)]"
          />
        )}

        <div className="space-y-1.5">
          <label className="text-xs font-bold text-[var(--sw-text-secondary)]">
            Veya Video URL (HTTPS)
          </label>
          <input
            type="url"
            value={videoUrl}
            onChange={(e) => setVideoUrl(e.target.value)}
            placeholder="https://.../video.mp4"
            className="w-full rounded-2xl bg-[var(--sw-surface-variant)] border border-[var(--sw-outline)] px-4 py-2.5 text-sm focus:outline-none focus:border-[var(--sw-primary)]"
          />
        </div>

        <div className="space-y-1.5">
          <label className="text-xs font-bold text-[var(--sw-text-secondary)]">Açıklama</label>
          <textarea
            rows={3}
            maxLength={300}
            value={caption}
            onChange={(e) => setCaption(e.target.value)}
            placeholder="Videon hakkında bir şeyler yaz..."
            className="w-full rounded-2xl bg-[var(--sw-surface-variant)] border border-[var(--sw-outline)] px-4 py-2.5 text-sm focus:outline-none focus:border-[var(--sw-primary)]"
          />
        </div>

        <div className="space-y-1.5">
          <label className="text-xs font-bold text-[var(--sw-text-secondary)]">
            Etiketler (virgülle ayır, en fazla 5)
          </label>
          <input
            type="text"
            value={tagsInput}
            onChange={(e) => setTagsInput(e.target.value)}
            placeholder="dans, müzik, vlog"
            className="w-full rounded-2xl bg-[var(--sw-surface-variant)] border border-[var(--sw-outline)] px-4 py-2.5 text-sm focus:outline-none focus:border-[var(--sw-primary)]"
          />
        </div>

        {error && (
          <div className="rounded-xl bg-red-500/15 border border-red-500/30 text-red-400 text-xs p-3">
            {error}
          </div>
        )}

        <button
          type="button"
          disabled={isPublishing || (!file && !videoUrl.trim())}
          onClick={handleSubmit}
          className="w-full h-12 rounded-2xl bg-[var(--sw-primary)] disabled:opacity-50 text-white font-bold text-sm inline-flex items-center justify-center gap-2 cursor-pointer"
        >
          <Upload className="w-4 h-4" />
          {isPublishing ? 'Yükleniyor...' : 'Videoyu Paylaş'}
        </button>
      </div>
    </div>
  );
}
