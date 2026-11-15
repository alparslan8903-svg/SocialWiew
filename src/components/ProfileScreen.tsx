import { useState } from 'react';
import { ArrowLeft, LogOut, Moon, RefreshCw, ShieldCheck, Sun, Monitor, Trash2 } from 'lucide-react';
import {
  isAdminUsername,
  isVerifiedUsername,
  saveSharedUpdateRepo,
  setUserVerified,
  updateProfile,
} from '../lib/socialRepository';
import { fetchGitHubRelease } from '../lib/mediaUtils';
import type { AppUpdateInfo, ThemeMode, UserPublic, VideoPost } from '../types/social';
import { CreatorAvatar, VerifiedAdminBadge, VerifiedTickIcon, VideoGridCard } from './CommonUI';

export function ProfileScreen({
  profile,
  videos,
  currentUserId,
  isAdmin,
  isFollowing,
  themeMode,
  configuredGithubRepo,
  onChangeThemeMode,
  onToggleFollow,
  onDeleteVideo,
  onOpenVideoPlayer,
  onSignOut,
  onNotify,
  onUpdateFound,
  onBack,
}: {
  profile: UserPublic | null;
  videos: VideoPost[];
  currentUserId: string;
  isAdmin: boolean;
  isFollowing: boolean;
  themeMode: ThemeMode;
  configuredGithubRepo: string;
  onChangeThemeMode: (mode: ThemeMode) => void;
  onToggleFollow: () => void;
  onDeleteVideo: (videoId: string) => void;
  onOpenVideoPlayer: (video: VideoPost) => void;
  onSignOut: () => void;
  onNotify: (msg: string) => void;
  onUpdateFound: (info: AppUpdateInfo) => void;
  onBack?: () => void;
}) {
  const [editing, setEditing] = useState(false);
  const [editName, setEditName] = useState('');
  const [editUsername, setEditUsername] = useState('');
  const [editBio, setEditBio] = useState('');
  const [repoInput, setRepoInput] = useState(configuredGithubRepo);
  const [checkingUpdate, setCheckingUpdate] = useState(false);

  if (!profile) {
    return (
      <div className="p-8 text-center text-sm text-[var(--sw-text-secondary)]">
        Profil yükleniyor...
      </div>
    );
  }

  const isOwn = profile.userId === currentUserId;
  const verified = profile.isVerified || isVerifiedUsername(profile.username);

  const openEdit = () => {
    setEditName(profile.displayName);
    setEditUsername(profile.username);
    setEditBio(profile.bio);
    setEditing(true);
  };

  const handleSaveProfile = async () => {
    try {
      await updateProfile(editName, editUsername, editBio);
      setEditing(false);
      onNotify('Profil güncellendi.');
    } catch (err) {
      onNotify(err instanceof Error ? err.message : 'Profil güncellenemedi.');
    }
  };

  const handleCheckUpdates = async () => {
    const repo = (repoInput || configuredGithubRepo).trim();
    if (!repo) {
      onNotify('Lütfen GitHub deposu girin.');
      return;
    }
    setCheckingUpdate(true);
    try {
      const info = await fetchGitHubRelease(repo);
      onUpdateFound(info);
      onNotify(info.isNewerAvailable ? `Yeni sürüm bulundu: ${info.tagName}` : `Güncel sürüm: ${info.tagName}`);
    } catch (err) {
      onNotify(err instanceof Error ? err.message : 'Güncelleme kontrolü başarısız.');
    } finally {
      setCheckingUpdate(false);
    }
  };

  return (
    <div className="p-6 max-w-5xl mx-auto space-y-6">
      {onBack && (
        <button
          type="button"
          onClick={onBack}
          className="inline-flex items-center gap-2 text-xs font-bold text-[var(--sw-text-secondary)] cursor-pointer"
        >
          <ArrowLeft className="w-4 h-4" /> Geri Dön
        </button>
      )}

      <div className="rounded-3xl bg-[var(--sw-surface)] border border-[var(--sw-outline)] p-6 flex flex-col sm:flex-row sm:items-center justify-between gap-6">
        <div className="flex items-center gap-4">
          <CreatorAvatar name={profile.displayName} avatarUrl={profile.avatarUrl} size={76} />
          <div>
            <div className="flex items-center gap-2">
              <h2 className="font-display text-2xl font-bold">{profile.displayName}</h2>
              {verified && <VerifiedTickIcon size={20} />}
            </div>
            <div className="text-sm text-[var(--sw-secondary)]">@{profile.username}</div>
            {verified && (
              <VerifiedAdminBadge
                label={isAdminUsername(profile.username) ? 'Admin' : 'Doğrulanmış'}
              />
            )}
            {profile.bio && <p className="text-sm mt-2 max-w-md">{profile.bio}</p>}
            <div className="flex items-center gap-5 mt-3 text-xs tabular-nums">
              <span>
                <strong>{videos.length}</strong> Video
              </span>
              <span>
                <strong>{profile.followersCount}</strong> Takipçi
              </span>
              <span>
                <strong>{profile.followingCount}</strong> Takip
              </span>
            </div>
          </div>
        </div>

        <div className="flex flex-wrap gap-2">
          {isOwn ? (
            <>
              <button
                type="button"
                onClick={openEdit}
                className="px-4 py-2.5 rounded-xl bg-[var(--sw-primary)] text-white text-xs font-bold cursor-pointer"
              >
                Profili Düzenle
              </button>
              <button
                type="button"
                onClick={onSignOut}
                className="px-4 py-2.5 rounded-xl bg-[var(--sw-surface-variant)] text-xs font-bold inline-flex items-center gap-1.5 cursor-pointer"
              >
                <LogOut className="w-3.5 h-3.5" /> Çıkış Yap
              </button>
            </>
          ) : (
            <>
              <button
                type="button"
                onClick={onToggleFollow}
                className={`px-4 py-2.5 rounded-xl text-xs font-bold cursor-pointer ${
                  isFollowing ? 'bg-[var(--sw-surface-variant)]' : 'bg-[var(--sw-primary)] text-white'
                }`}
              >
                {isFollowing ? 'Takip Ediliyor' : 'Takip Et'}
              </button>
              {isAdmin && (
                <button
                  type="button"
                  onClick={() =>
                    setUserVerified(profile.userId, !profile.isVerified)
                      .then(() => onNotify(profile.isVerified ? 'Mavi tik kaldırıldı.' : 'Mavi tik verildi.'))
                      .catch((e) => onNotify(e instanceof Error ? e.message : 'Hata'))
                  }
                  className="px-4 py-2.5 rounded-xl border border-[var(--sw-outline)] text-xs font-bold inline-flex items-center gap-1.5 cursor-pointer"
                >
                  <ShieldCheck className="w-3.5 h-3.5" />
                  {profile.isVerified ? 'Mavi Tiki Kaldır' : 'Mavi Tik Ver'}
                </button>
              )}
            </>
          )}
        </div>
      </div>

      {isOwn && (
        <div className="grid md:grid-cols-2 gap-4">
          <div className="rounded-3xl bg-[var(--sw-surface)] border border-[var(--sw-outline)] p-5 space-y-3">
            <h3 className="font-display text-sm font-bold">Görünüm Teması</h3>
            <div className="grid grid-cols-3 gap-2">
              {(
                [
                  ['dark', 'Koyu', Moon],
                  ['light', 'Açık', Sun],
                  ['system', 'Sistem', Monitor],
                ] as const
              ).map(([mode, label, IconComp]) => (
                <button
                  key={mode}
                  type="button"
                  onClick={() => onChangeThemeMode(mode)}
                  className={`py-2.5 px-3 rounded-xl text-xs font-bold inline-flex items-center justify-center gap-1.5 cursor-pointer border ${
                    themeMode === mode
                      ? 'bg-[var(--sw-primary)] text-white border-transparent'
                      : 'bg-[var(--sw-surface-variant)] border-[var(--sw-outline)]'
                  }`}
                >
                  <IconComp className="w-3.5 h-3.5" />
                  {label}
                </button>
              ))}
            </div>
          </div>

          <div className="rounded-3xl bg-[var(--sw-surface)] border border-[var(--sw-outline)] p-5 space-y-3">
            <h3 className="font-display text-sm font-bold">GitHub Güncellemeleri</h3>
            <div className="flex gap-2">
              <input
                type="text"
                value={repoInput}
                onChange={(e) => setRepoInput(e.target.value)}
                placeholder={configuredGithubRepo || 'kullanici/SocialWiew'}
                disabled={!isAdmin}
                className="flex-1 rounded-xl bg-[var(--sw-surface-variant)] border border-[var(--sw-outline)] px-3 py-2 text-xs"
              />
              {isAdmin && (
                <button
                  type="button"
                  onClick={() =>
                    saveSharedUpdateRepo(repoInput)
                      .then(() => onNotify('Depo kaydedildi.'))
                      .catch((e) => onNotify(e instanceof Error ? e.message : 'Hata'))
                  }
                  className="px-3 py-2 rounded-xl bg-[var(--sw-surface-variant)] text-xs font-bold cursor-pointer"
                >
                  Kaydet
                </button>
              )}
              <button
                type="button"
                disabled={checkingUpdate}
                onClick={handleCheckUpdates}
                className="px-3 py-2 rounded-xl bg-[var(--sw-secondary)] text-black text-xs font-bold inline-flex items-center gap-1 cursor-pointer"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${checkingUpdate ? 'animate-spin' : ''}`} />
                Kontrol Et
              </button>
            </div>
          </div>
        </div>
      )}

      <section className="space-y-3">
        <h3 className="font-display text-base font-bold">Videolar ({videos.length})</h3>
        {videos.length === 0 ? (
          <div className="rounded-2xl bg-[var(--sw-surface)] border border-[var(--sw-outline)] p-8 text-center text-sm text-[var(--sw-text-secondary)]">
            Henüz paylaşılmış video yok.
          </div>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
            {videos.map((video) => (
              <div key={video.id} className="relative">
                <VideoGridCard video={video} onClick={() => onOpenVideoPlayer(video)} />
                {isOwn && (
                  <button
                    type="button"
                    onClick={() => onDeleteVideo(video.id)}
                    className="absolute top-2.5 left-2.5 w-8 h-8 rounded-full bg-black/70 text-white hover:text-red-400 flex items-center justify-center cursor-pointer"
                    aria-label="Videoyu sil"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </section>

      {editing && (
        <div className="fixed inset-0 z-50 bg-black/70 flex items-center justify-center p-4">
          <div className="w-full max-w-md rounded-3xl bg-[var(--sw-surface)] border border-[var(--sw-outline)] p-6 space-y-4">
            <h3 className="font-display text-lg font-bold">Profili Düzenle</h3>
            <input
              type="text"
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              placeholder="Görünen Ad"
              className="w-full rounded-xl bg-[var(--sw-surface-variant)] border border-[var(--sw-outline)] px-3.5 py-2 text-sm"
            />
            <input
              type="text"
              value={editUsername}
              onChange={(e) => setEditUsername(e.target.value)}
              placeholder="Kullanıcı Adı"
              className="w-full rounded-xl bg-[var(--sw-surface-variant)] border border-[var(--sw-outline)] px-3.5 py-2 text-sm"
            />
            <textarea
              rows={3}
              value={editBio}
              onChange={(e) => setEditBio(e.target.value)}
              placeholder="Biyografi"
              className="w-full rounded-xl bg-[var(--sw-surface-variant)] border border-[var(--sw-outline)] px-3.5 py-2 text-sm"
            />
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setEditing(false)}
                className="px-4 py-2 rounded-xl border border-[var(--sw-outline)] text-xs font-bold cursor-pointer"
              >
                İptal
              </button>
              <button
                type="button"
                onClick={handleSaveProfile}
                className="px-4 py-2 rounded-xl bg-[var(--sw-primary)] text-white text-xs font-bold cursor-pointer"
              >
                Kaydet
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
