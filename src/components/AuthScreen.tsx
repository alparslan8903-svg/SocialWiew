import { useState } from 'react';
import { GoogleAuthProvider, signInWithPopup } from 'firebase/auth';
import { Sparkles, UserPlus, Film } from 'lucide-react';
import { auth } from '../lib/firebase';

export function AuthScreen() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleGoogleSignIn = async () => {
    setError(null);
    setLoading(true);
    try {
      const provider = new GoogleAuthProvider();
      await signInWithPopup(auth, provider);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Google ile giriş yapılırken bir hata oluştu.';
      if (!message.toLowerCase().includes('popup-closed-by-user')) {
        setError(message);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[var(--sw-bg)] text-[var(--sw-text-primary)] flex items-center justify-center px-4 py-8">
      <div className="w-full max-w-5xl grid md:grid-cols-2 gap-8 items-center">
        <div>
          <div className="relative h-64 rounded-3xl overflow-hidden border border-[var(--sw-outline)] shadow-xl">
            <img
              src="/assets/img_auth_hero.jpg"
              alt="SocialWiew görseli"
              className="w-full h-full object-cover"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/30 to-transparent" />
            <div className="absolute bottom-5 left-5 flex items-center gap-3">
              <div className="w-14 h-14 rounded-2xl bg-white p-1 shadow-lg shrink-0">
                <img
                  src="/assets/ic_socialwiew_logo.jpg"
                  alt="SocialWiew logosu"
                  className="w-full h-full object-cover rounded-xl"
                />
              </div>
              <div>
                <h1 className="font-display text-3xl font-bold text-white leading-tight">
                  SocialWiew
                </h1>
                <p className="text-sm font-semibold text-[#00e5c3]">
                  Yaratıcı Video Topluluğu
                </p>
              </div>
            </div>
          </div>

          <h2 className="font-display text-3xl font-bold mt-6 leading-tight">
            Anını çek,
            <br />
            dünyayla paylaş.
          </h2>
          <p className="text-sm text-[var(--sw-text-secondary)] mt-2">
            Kısa videolar paylaş, takip et ve GIF&apos;lerle tepki ver.
          </p>
        </div>

        <div className="rounded-3xl bg-[var(--sw-surface)] border border-[var(--sw-outline)] p-7 shadow-xl space-y-4">
          <div className="flex items-center gap-3.5 p-3.5 rounded-2xl bg-[var(--sw-surface-variant)]">
            <div className="w-10 h-10 rounded-xl bg-[var(--sw-primary-container)] text-[var(--sw-primary)] flex items-center justify-center shrink-0">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <div className="text-sm font-bold">Kısa Videolar Yükle</div>
              <div className="text-xs text-[var(--sw-text-secondary)]">
                Cihazından veya video bağlantısından anında paylaş
              </div>
            </div>
          </div>

          <div className="flex items-center gap-3.5 p-3.5 rounded-2xl bg-[var(--sw-surface-variant)]">
            <div className="w-10 h-10 rounded-xl bg-[var(--sw-primary-container)] text-[var(--sw-primary)] flex items-center justify-center shrink-0">
              <UserPlus className="w-5 h-5" />
            </div>
            <div>
              <div className="text-sm font-bold">İçerik Üreticilerini Takip Et</div>
              <div className="text-xs text-[var(--sw-text-secondary)]">
                Sevdiğin profillerin yeni videolarını kaçırma
              </div>
            </div>
          </div>

          <div className="flex items-center gap-3.5 p-3.5 rounded-2xl bg-[var(--sw-surface-variant)]">
            <div className="w-10 h-10 rounded-xl bg-[var(--sw-secondary-container)] text-[var(--sw-secondary)] flex items-center justify-center shrink-0">
              <Film className="w-5 h-5" />
            </div>
            <div>
              <div className="text-sm font-bold">Tenor GIF ile Yorum Yap</div>
              <div className="text-xs text-[var(--sw-text-secondary)]">
                Videolara hareketli Tenor GIF&apos;leriyle anında tepki ver
              </div>
            </div>
          </div>

          {error && (
            <div className="rounded-xl bg-red-500/15 border border-red-500/30 text-red-400 text-xs p-3">
              {error}
            </div>
          )}

          <button
            type="button"
            onClick={handleGoogleSignIn}
            disabled={loading}
            className="w-full h-13 rounded-2xl bg-[var(--sw-primary)] hover:opacity-95 disabled:opacity-60 text-white font-bold text-sm transition cursor-pointer flex items-center justify-center gap-2 mt-2"
          >
            {loading ? 'Giriş yapılıyor...' : 'Google ile Giriş Yap'}
          </button>
        </div>
      </div>
    </div>
  );
}
