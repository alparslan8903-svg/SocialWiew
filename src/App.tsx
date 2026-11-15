import { useEffect, useMemo, useState } from 'react';
import { onAuthStateChanged, signOut, type User } from 'firebase/auth';
import { Compass, Home, PlusCircle, Search, User as UserIcon, X } from 'lucide-react';
import { auth, testFirestoreConnection } from './lib/firebase';
import {
  addComment,
  deleteComment,
  deleteVideo,
  ensureUserProfile,
  isCurrentUserAdmin,
  isVerifiedUsername,
  subscribeComments,
  subscribeCreators,
  subscribeFollowingIds,
  subscribeLikedVideoIds,
  subscribePublicVideos,
  subscribeSharedUpdateRepo,
  toggleFollow,
  toggleLike,
} from './lib/socialRepository';
import type {
  AppUpdateInfo,
  TenorGifItem,
  ThemeMode,
  UserPublic,
  VideoComment,
  VideoPost,
} from './types/social';
import { AuthScreen } from './components/AuthScreen';
import { CommentsModal } from './components/CommentsModal';
import { GitHubUpdateBanner, GitHubUpdateModal, VideoPlayerSurface } from './components/CommonUI';
import { DiscoverScreen } from './components/DiscoverScreen';
import { FeedScreen } from './components/FeedScreen';
import { ProfileScreen } from './components/ProfileScreen';
import { SearchScreen } from './components/SearchScreen';
import { UploadScreen } from './components/UploadScreen';

type AppTab = 'feed' | 'discover' | 'search' | 'upload' | 'profile';

const THEME_KEY = 'socialwiew_theme_mode';

export function App() {
  const [user, setUser] = useState<User | null>(auth.currentUser);
  const [authReady, setAuthReady] = useState(false);
  const [tab, setTab] = useState<AppTab>('feed');
  const [creatorId, setCreatorId] = useState<string | null>(null);

  const [themeMode, setThemeMode] = useState<ThemeMode>(() => {
    const stored = localStorage.getItem(THEME_KEY);
    return stored === 'light' || stored === 'system' || stored === 'dark' ? stored : 'dark';
  });

  const [currentProfile, setCurrentProfile] = useState<UserPublic | null>(null);
  const [videos, setVideos] = useState<VideoPost[]>([]);
  const [videosLoading, setVideosLoading] = useState(true);
  const [videosError, setVideosError] = useState<string | null>(null);
  const [creators, setCreators] = useState<UserPublic[]>([]);
  const [followingIds, setFollowingIds] = useState<Set<string>>(new Set());
  const [likedVideoIds, setLikedVideoIds] = useState<Set<string>>(new Set());
  const [configuredGithubRepo, setConfiguredGithubRepo] = useState('');
  const [updateBanner, setUpdateBanner] = useState<AppUpdateInfo | null>(null);
  const [updateModalInfo, setUpdateModalInfo] = useState<AppUpdateInfo | null>(null);

  const [fullScreenVideo, setFullScreenVideo] = useState<VideoPost | null>(null);
  const [commentsVideo, setCommentsVideo] = useState<VideoPost | null>(null);
  const [comments, setComments] = useState<VideoComment[]>([]);
  const [toast, setToast] = useState<string | null>(null);

  useEffect(() => {
    const root = document.documentElement;
    const apply = (dark: boolean) => {
      root.classList.toggle('dark', dark);
    };
    if (themeMode === 'dark') apply(true);
    else if (themeMode === 'light') apply(false);
    else {
      const media = window.matchMedia('(prefers-color-scheme: dark)');
      apply(media.matches);
      const handler = (e: MediaQueryListEvent) => apply(e.matches);
      media.addEventListener('change', handler);
      return () => media.removeEventListener('change', handler);
    }
  }, [themeMode]);

  const showToast = (msg: string) => {
    setToast(msg);
    window.setTimeout(() => {
      setToast((prev) => (prev === msg ? null : prev));
    }, 3200);
  };

  useEffect(() => {
    const unsub = onAuthStateChanged(auth, (nextUser) => {
      setUser(nextUser);
      setAuthReady(true);
    });
    return unsub;
  }, []);

  useEffect(() => {
    if (!user) {
      setCurrentProfile(null);
      setVideos([]);
      setCreators([]);
      setFollowingIds(new Set());
      setLikedVideoIds(new Set());
      return;
    }

    let cancelled = false;
    testFirestoreConnection();
    ensureUserProfile(user)
      .then((profile) => {
        if (!cancelled) setCurrentProfile(profile);
      })
      .catch((err) => {
        if (!cancelled) showToast(err instanceof Error ? err.message : 'Profil başlatılamadı.');
      });

    setVideosLoading(true);
    const unsubVideos = subscribePublicVideos(
      (list) => {
        setVideos(list);
        setVideosLoading(false);
        setVideosError(null);
      },
      (err) => {
        setVideosError(err.message);
        setVideosLoading(false);
      }
    );

    const unsubCreators = subscribeCreators(
      (list) => {
        setCreators(list);
        const mine = list.find((c) => c.userId === user.uid);
        if (mine) setCurrentProfile(mine);
      },
      () => {}
    );

    const unsubFollowing = subscribeFollowingIds(user.uid, setFollowingIds);
    const unsubLikes = subscribeLikedVideoIds(user.uid, setLikedVideoIds);
    const unsubRepo = subscribeSharedUpdateRepo(setConfiguredGithubRepo);

    return () => {
      cancelled = true;
      unsubVideos();
      unsubCreators();
      unsubFollowing();
      unsubLikes();
      unsubRepo();
    };
  }, [user]);

  useEffect(() => {
    if (!commentsVideo) {
      setComments([]);
      return;
    }
    const unsub = subscribeComments(commentsVideo.id, setComments);
    return unsub;
  }, [commentsVideo]);

  const verifiedUserIds = useMemo(
    () =>
      new Set(
        creators
          .filter((c) => c.isVerified || isVerifiedUsername(c.username))
          .map((c) => c.userId)
      ),
    [creators]
  );

  if (!authReady) {
    return (
      <div className="min-h-screen bg-[var(--sw-bg)] flex items-center justify-center text-sm text-[var(--sw-text-secondary)]">
        SocialWiew yükleniyor...
      </div>
    );
  }

  if (!user) {
    return <AuthScreen />;
  }

  const openCreatorProfile = (uid: string) => {
    if (uid === user.uid) {
      setCreatorId(null);
      setTab('profile');
    } else {
      setCreatorId(uid);
    }
  };

  const activeCreatorProfile =
    creatorId ? creators.find((c) => c.userId === creatorId) ?? null : currentProfile;

  const navItems: Array<{ id: AppTab; label: string; icon: typeof Home }> = [
    { id: 'feed', label: 'Akış', icon: Home },
    { id: 'discover', label: 'Keşfet', icon: Compass },
    { id: 'search', label: 'Ara', icon: Search },
    { id: 'upload', label: 'Yükle', icon: PlusCircle },
    { id: 'profile', label: 'Profil', icon: UserIcon },
  ];

  return (
    <div className="min-h-screen bg-[var(--sw-bg)] text-[var(--sw-text-primary)] flex flex-col md:flex-row">
      <aside className="hidden md:flex md:w-24 lg:w-56 shrink-0 flex-col border-r border-[var(--sw-outline)] bg-[var(--sw-surface)] p-4 gap-2">
        <div className="flex items-center gap-3 px-2 py-2 mb-4">
          <img
            src="/assets/ic_socialwiew_logo.jpg"
            alt="SocialWiew"
            className="w-10 h-10 rounded-xl object-cover shrink-0"
          />
          <span className="font-display font-bold text-lg hidden lg:inline">SocialWiew</span>
        </div>
        {navItems.map((item) => {
          const Icon = item.icon;
          const active = tab === item.id && !creatorId;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => {
                setCreatorId(null);
                setTab(item.id);
              }}
              className={`flex items-center gap-3 px-3.5 py-3 rounded-2xl text-sm font-semibold cursor-pointer transition ${
                active
                  ? 'bg-[var(--sw-primary-container)] text-[var(--sw-primary)]'
                  : 'hover:bg-[var(--sw-surface-variant)] text-[var(--sw-text-secondary)]'
              }`}
            >
              <Icon className="w-5 h-5 shrink-0" />
              <span className="hidden lg:inline">{item.label}</span>
            </button>
          );
        })}
      </aside>

      <main className="flex-1 min-w-0 pb-16 md:pb-0">
        <GitHubUpdateBanner
          info={updateBanner}
          onOpenDetails={() => setUpdateModalInfo(updateBanner)}
          onDismiss={() => setUpdateBanner(null)}
        />

        {creatorId ? (
          <ProfileScreen
            profile={activeCreatorProfile}
            videos={videos.filter((v) => v.userId === creatorId)}
            currentUserId={user.uid}
            isAdmin={isCurrentUserAdmin(user)}
            isFollowing={followingIds.has(creatorId)}
            themeMode={themeMode}
            configuredGithubRepo={configuredGithubRepo}
            onChangeThemeMode={(m) => {
              setThemeMode(m);
              localStorage.setItem(THEME_KEY, m);
            }}
            onToggleFollow={() =>
              toggleFollow(creatorId, followingIds.has(creatorId)).catch((e) =>
                showToast(e instanceof Error ? e.message : 'İşlem başarısız.')
              )
            }
            onDeleteVideo={(vid) =>
              deleteVideo(vid).catch((e) => showToast(e instanceof Error ? e.message : 'Silinemedi.'))
            }
            onOpenVideoPlayer={setFullScreenVideo}
            onSignOut={() => signOut(auth)}
            onNotify={showToast}
            onUpdateFound={setUpdateBanner}
            onBack={() => setCreatorId(null)}
          />
        ) : tab === 'feed' ? (
          <FeedScreen
            videos={videos}
            loading={videosLoading}
            error={videosError}
            currentUserId={user.uid}
            verifiedUserIds={verifiedUserIds}
            followingIds={followingIds}
            likedVideoIds={likedVideoIds}
            onToggleLike={(v) =>
              toggleLike(v.id, likedVideoIds.has(v.id)).catch((e) =>
                showToast(e instanceof Error ? e.message : 'Beğeni başarısız.')
              )
            }
            onToggleFollow={(uid) =>
              toggleFollow(uid, followingIds.has(uid)).catch((e) =>
                showToast(e instanceof Error ? e.message : 'Takip başarısız.')
              )
            }
            onOpenComments={setCommentsVideo}
            onOpenCreatorProfile={openCreatorProfile}
            onNavigateToUpload={() => setTab('upload')}
            onNavigateToDiscover={() => setTab('discover')}
            onNavigateToSearch={() => setTab('search')}
            onShareFeedback={showToast}
          />
        ) : tab === 'discover' ? (
          <DiscoverScreen
            creators={creators}
            videos={videos}
            currentUserId={user.uid}
            followingIds={followingIds}
            onToggleFollow={(uid) =>
              toggleFollow(uid, followingIds.has(uid)).catch((e) =>
                showToast(e instanceof Error ? e.message : 'Takip başarısız.')
              )
            }
            onOpenCreatorProfile={openCreatorProfile}
            onOpenVideoPlayer={setFullScreenVideo}
            onNavigateToSearch={() => setTab('search')}
          />
        ) : tab === 'search' ? (
          <SearchScreen
            creators={creators}
            videos={videos}
            currentUserId={user.uid}
            followingIds={followingIds}
            onToggleFollow={(uid) =>
              toggleFollow(uid, followingIds.has(uid)).catch((e) =>
                showToast(e instanceof Error ? e.message : 'Takip başarısız.')
              )
            }
            onOpenCreatorProfile={openCreatorProfile}
            onOpenVideoPlayer={setFullScreenVideo}
          />
        ) : tab === 'upload' ? (
          <UploadScreen
            author={currentProfile}
            onPublished={() => setTab('feed')}
            onNotify={showToast}
          />
        ) : (
          <ProfileScreen
            profile={currentProfile}
            videos={videos.filter((v) => v.userId === user.uid)}
            currentUserId={user.uid}
            isAdmin={isCurrentUserAdmin(user)}
            isFollowing={false}
            themeMode={themeMode}
            configuredGithubRepo={configuredGithubRepo}
            onChangeThemeMode={(m) => {
              setThemeMode(m);
              localStorage.setItem(THEME_KEY, m);
            }}
            onToggleFollow={() => {}}
            onDeleteVideo={(vid) =>
              deleteVideo(vid)
                .then(() => showToast('Video silindi.'))
                .catch((e) => showToast(e instanceof Error ? e.message : 'Silinemedi.'))
            }
            onOpenVideoPlayer={setFullScreenVideo}
            onSignOut={() => signOut(auth)}
            onNotify={showToast}
            onUpdateFound={setUpdateBanner}
          />
        )}
      </main>

      <nav className="md:hidden fixed bottom-0 inset-x-0 z-30 h-16 bg-[var(--sw-surface)] border-t border-[var(--sw-outline)] grid grid-cols-5">
        {navItems.map((item) => {
          const Icon = item.icon;
          const active = tab === item.id && !creatorId;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => {
                setCreatorId(null);
                setTab(item.id);
              }}
              className={`flex flex-col items-center justify-center gap-1 text-[11px] font-semibold cursor-pointer ${
                active ? 'text-[var(--sw-primary)]' : 'text-[var(--sw-text-secondary)]'
              }`}
            >
              <Icon className="w-5 h-5" />
              <span>{item.label}</span>
            </button>
          );
        })}
      </nav>

      {fullScreenVideo && (
        <div className="fixed inset-0 z-40 bg-[#06070b] flex items-center justify-center">
          <button
            type="button"
            onClick={() => setFullScreenVideo(null)}
            className="absolute top-4 left-4 z-50 w-10 h-10 rounded-full bg-black/60 text-white flex items-center justify-center cursor-pointer"
            aria-label="Videoyu kapat"
          >
            <X className="w-5 h-5" />
          </button>
          <div className="w-full h-full max-w-lg max-h-[92vh]">
            <VideoPlayerSurface video={fullScreenVideo} isActive={!commentsVideo} isMuted={false} />
          </div>
        </div>
      )}

      {commentsVideo && (
        <CommentsModal
          video={videos.find((v) => v.id === commentsVideo.id) ?? commentsVideo}
          currentUserId={user.uid}
          verifiedUserIds={verifiedUserIds}
          comments={comments}
          onSendComment={async (text: string, gif: TenorGifItem | null) => {
            if (!currentProfile) {
              showToast('Profiliniz henüz yüklenmedi.');
              return;
            }
            await addComment({
              videoId: commentsVideo.id,
              author: currentProfile,
              text,
              gifUrl: gif?.fullUrl,
              gifPreviewUrl: gif?.previewUrl,
            });
          }}
          onDeleteComment={(commentId) => deleteComment(commentsVideo.id, commentId)}
          onClose={() => setCommentsVideo(null)}
        />
      )}

      <GitHubUpdateModal info={updateModalInfo} onClose={() => setUpdateModalInfo(null)} />

      {toast && (
        <div className="fixed bottom-20 md:bottom-6 left-1/2 -translate-x-1/2 z-50 px-4 py-2.5 rounded-2xl bg-[var(--sw-surface)] border border-[var(--sw-outline)] shadow-xl text-xs font-semibold">
          {toast}
        </div>
      )}
    </div>
  );
}
export default App;
