import { useEffect, useState } from 'react';
import { Send, Trash2, X } from 'lucide-react';
import { fetchTenorGifs } from '../lib/mediaUtils';
import { isVerifiedUsername } from '../lib/socialRepository';
import type { TenorGifItem, VideoComment, VideoPost } from '../types/social';
import { CreatorAvatar, VerifiedTickIcon } from './CommonUI';

const QUICK_MOODS = [
  { label: 'Trend', query: '' },
  { label: 'Alkış', query: 'clap' },
  { label: 'Kahkaha', query: 'laugh' },
  { label: 'Kalp', query: 'heart' },
  { label: 'Şaşkın', query: 'wow' },
  { label: 'Dans', query: 'dance' },
];

export function CommentsModal({
  video,
  currentUserId,
  verifiedUserIds,
  comments,
  onSendComment,
  onDeleteComment,
  onClose,
}: {
  video: VideoPost;
  currentUserId: string;
  verifiedUserIds: Set<string>;
  comments: VideoComment[];
  onSendComment: (text: string, gif: TenorGifItem | null) => Promise<void>;
  onDeleteComment: (commentId: string) => Promise<void>;
  onClose: () => void;
}) {
  const [text, setText] = useState('');
  const [showGifPicker, setShowGifPicker] = useState(false);
  const [gifQuery, setGifQuery] = useState('');
  const [gifs, setGifs] = useState<TenorGifItem[]>([]);
  const [gifsLoading, setGifsLoading] = useState(false);
  const [gifsError, setGifsError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  useEffect(() => {
    if (!showGifPicker) return;
    let cancelled = false;
    const timer = window.setTimeout(async () => {
      setGifsLoading(true);
      setGifsError(null);
      try {
        const results = await fetchTenorGifs(gifQuery);
        if (!cancelled) setGifs(results);
      } catch (err) {
        if (!cancelled) {
          setGifsError(err instanceof Error ? err.message : "Tenor GIF'leri yüklenemedi.");
        }
      } finally {
        if (!cancelled) setGifsLoading(false);
      }
    }, 260);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [showGifPicker, gifQuery]);

  const handleSendText = async () => {
    if (!text.trim() || sending) return;
    setSending(true);
    try {
      await onSendComment(text.trim(), null);
      setText('');
    } finally {
      setSending(false);
    }
  };

  const handleSelectGif = async (gif: TenorGifItem) => {
    if (sending) return;
    setSending(true);
    try {
      await onSendComment(text.trim(), gif);
      setText('');
      setShowGifPicker(false);
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/70 flex items-end md:items-center justify-center p-0 md:p-4">
      <div className="w-full max-w-xl h-[82vh] md:h-[640px] rounded-t-3xl md:rounded-3xl bg-[var(--sw-surface)] border border-[var(--sw-outline)] flex flex-col overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b border-[var(--sw-outline)]">
          <h3 className="font-display font-bold text-base tabular-nums">
            Yorumlar ({video.commentsCount})
          </h3>
          <button type="button" onClick={onClose} className="p-1.5 cursor-pointer" aria-label="Kapat">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {comments.length === 0 ? (
            <div className="h-full flex items-center justify-center text-sm text-[var(--sw-text-secondary)] text-center px-6">
              Henüz yorum yok. İlk yorumu veya Tenor GIF&apos;i sen at!
            </div>
          ) : (
            comments.map((comment) => {
              const verified =
                comment.authorVerified ||
                verifiedUserIds.has(comment.userId) ||
                isVerifiedUsername(comment.authorUsername);
              return (
                <div key={comment.id} className="flex items-start gap-3">
                  <CreatorAvatar name={comment.authorName} avatarUrl={comment.authorAvatarUrl} size={36} />
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-1.5">
                      <span className="text-xs font-bold text-[var(--sw-text-secondary)] truncate">
                        @{comment.authorUsername}
                      </span>
                      {verified && <VerifiedTickIcon size={14} />}
                    </div>
                    {comment.text && <p className="text-sm mt-0.5 break-words">{comment.text}</p>}
                    {comment.gifUrl && (
                      <img
                        src={comment.gifUrl}
                        alt="Tenor GIF yorumu"
                        className="mt-2 max-w-[220px] max-h-[200px] rounded-xl object-contain bg-[var(--sw-surface-variant)]"
                      />
                    )}
                  </div>
                  {comment.userId === currentUserId && (
                    <button
                      type="button"
                      onClick={() => onDeleteComment(comment.id)}
                      className="p-1.5 text-[var(--sw-text-secondary)] hover:text-red-400 cursor-pointer"
                      aria-label="Yorumu sil"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  )}
                </div>
              );
            })
          )}
        </div>

        {showGifPicker && (
          <div className="border-t border-[var(--sw-outline)] bg-[var(--sw-surface-variant)] p-3 space-y-2.5">
            <input
              type="text"
              value={gifQuery}
              onChange={(e) => setGifQuery(e.target.value)}
              placeholder="Tenor'da GIF ara..."
              className="w-full rounded-xl bg-[var(--sw-surface)] border border-[var(--sw-outline)] px-3 py-2 text-xs focus:outline-none focus:border-[var(--sw-primary)]"
            />
            <div className="flex gap-1.5 overflow-x-auto pb-1">
              {QUICK_MOODS.map((mood) => (
                <button
                  key={mood.label}
                  type="button"
                  onClick={() => setGifQuery(mood.query)}
                  className="px-2.5 py-1 rounded-lg text-xs font-semibold bg-[var(--sw-surface)] border border-[var(--sw-outline)] shrink-0 cursor-pointer"
                >
                  {mood.label}
                </button>
              ))}
            </div>
            <div className="h-36 overflow-y-auto">
              {gifsLoading ? (
                <div className="h-full flex items-center justify-center text-xs text-[var(--sw-text-secondary)]">
                  GIF&apos;ler yükleniyor...
                </div>
              ) : gifsError ? (
                <div className="h-full flex items-center justify-center text-xs text-red-400">
                  {gifsError}
                </div>
              ) : gifs.length === 0 ? (
                <div className="h-full flex items-center justify-center text-xs text-[var(--sw-text-secondary)]">
                  Eşleşen Tenor GIF bulunamadı.
                </div>
              ) : (
                <div className="grid grid-cols-3 gap-2">
                  {gifs.map((gif) => (
                    <button
                      key={gif.id}
                      type="button"
                      onClick={() => handleSelectGif(gif)}
                      className="aspect-square rounded-xl overflow-hidden bg-[var(--sw-surface)] border border-[var(--sw-outline)] cursor-pointer"
                    >
                      <img src={gif.previewUrl} alt={gif.title} className="w-full h-full object-cover" />
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        <div className="p-3 border-t border-[var(--sw-outline)] flex items-center gap-2">
          <button
            type="button"
            onClick={() => setShowGifPicker((v) => !v)}
            className={`px-3 py-2 rounded-xl text-xs font-bold border cursor-pointer ${
              showGifPicker
                ? 'bg-[var(--sw-secondary)] text-black border-transparent'
                : 'bg-[var(--sw-surface-variant)] border-[var(--sw-outline)]'
            }`}
          >
            GIF
          </button>
          <input
            type="text"
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleSendText();
            }}
            placeholder="Yorum yaz..."
            className="flex-1 rounded-2xl bg-[var(--sw-surface-variant)] border border-[var(--sw-outline)] px-3.5 py-2 text-sm focus:outline-none focus:border-[var(--sw-primary)]"
          />
          <button
            type="button"
            disabled={!text.trim() || sending}
            onClick={handleSendText}
            className="w-10 h-10 rounded-full bg-[var(--sw-primary)] disabled:opacity-40 text-white flex items-center justify-center cursor-pointer shrink-0"
            aria-label="Yorum gönder"
          >
            <Send className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
