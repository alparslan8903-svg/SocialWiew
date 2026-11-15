import type { User } from 'firebase/auth';
import {
  collection,
  doc,
  getDoc,
  getDocs,
  increment,
  onSnapshot,
  query,
  serverTimestamp,
  Timestamp,
  updateDoc,
  where,
  writeBatch,
} from 'firebase/firestore';
import { auth, db, handleFirestoreError, OperationType } from './firebase';
import type { UserPublic, VideoComment, VideoPost } from '../types/social';

export const ADMIN_EMAIL = 'alparslan8903@gmail.com';
const VERIFIED_USERNAMES = new Set(['alparslan8903', 'eymendogan615']);
const MAX_CLOUD_VIDEO_BYTES = 15_500_000;
const CHUNK_RAW_BYTES = 450_000;
const MAX_CHUNKS = 36;
const CHUNKS_PER_BATCH = 6;

const cloudBlobCache = new Map<string, string>();

export function isVerifiedUsername(username?: string | null): boolean {
  const clean = (username ?? '').trim().replace(/^@/, '').toLowerCase();
  return VERIFIED_USERNAMES.has(clean);
}

export function isAdminUsername(username?: string | null): boolean {
  return (username ?? '').trim().replace(/^@/, '').toLowerCase() === 'alparslan8903';
}

export function isCurrentUserAdmin(user: User | null): boolean {
  return (user?.email ?? '').trim().toLowerCase() === ADMIN_EMAIL;
}

function timestampToMs(val: unknown): number {
  if (val instanceof Timestamp) return val.toMillis();
  if (typeof val === 'number') return val;
  if (val && typeof val === 'object' && 'toMillis' in val && typeof (val as { toMillis: () => number }).toMillis === 'function') {
    return (val as { toMillis: () => number }).toMillis();
  }
  return Date.now();
}

function mapUserPublic(id: string, data: Record<string, unknown>): UserPublic {
  const username = String(data.username ?? '');
  const verified =
    data.isVerified === true ||
    data.verified === true ||
    isVerifiedUsername(username);
  return {
    id,
    userId: String(data.userId ?? id),
    displayName: String(data.displayName ?? ''),
    username,
    bio: String(data.bio ?? ''),
    avatarUrl: String(data.avatarUrl ?? ''),
    isVerified: verified,
    followersCount: Number(data.followersCount ?? 0),
    followingCount: Number(data.followingCount ?? 0),
    videosCount: Number(data.videosCount ?? 0),
    isPublic: data.isPublic !== false,
    createdAtMs: timestampToMs(data.createdAt),
    updatedAtMs: timestampToMs(data.updatedAt),
  };
}

function mapVideoPost(id: string, data: Record<string, unknown>): VideoPost {
  const authorUsername = String(data.authorUsername ?? '');
  const verified =
    data.authorVerified === true || isVerifiedUsername(authorUsername);
  return {
    id,
    userId: String(data.userId ?? ''),
    authorName: String(data.authorName ?? ''),
    authorUsername,
    authorAvatarUrl: String(data.authorAvatarUrl ?? ''),
    authorVerified: verified,
    caption: String(data.caption ?? ''),
    videoUrl: String(data.videoUrl ?? ''),
    localFileName: String(data.localFileName ?? ''),
    thumbnailData: String(data.thumbnailData ?? ''),
    chunkCount: Number(data.chunkCount ?? 0),
    durationMs: Number(data.durationMs ?? 0),
    tags: Array.isArray(data.tags) ? data.tags.map((t) => String(t)) : [],
    visibility: String(data.visibility ?? 'public'),
    likesCount: Number(data.likesCount ?? 0),
    commentsCount: Number(data.commentsCount ?? 0),
    createdAtMs: timestampToMs(data.createdAt),
    updatedAtMs: timestampToMs(data.updatedAt),
  };
}

function mapVideoComment(id: string, data: Record<string, unknown>): VideoComment {
  const authorUsername = String(data.authorUsername ?? '');
  const verified =
    data.authorVerified === true || isVerifiedUsername(authorUsername);
  return {
    id,
    videoId: String(data.videoId ?? ''),
    userId: String(data.userId ?? ''),
    authorName: String(data.authorName ?? ''),
    authorUsername,
    authorAvatarUrl: String(data.authorAvatarUrl ?? ''),
    authorVerified: verified,
    text: String(data.text ?? ''),
    gifUrl: String(data.gifUrl ?? ''),
    gifPreviewUrl: String(data.gifPreviewUrl ?? ''),
    visibility: String(data.visibility ?? 'public'),
    createdAtMs: timestampToMs(data.createdAt),
  };
}

function requireUserId(): string {
  const uid = auth.currentUser?.uid;
  if (!uid) {
    throw new Error('Devam etmek için Google ile giriş yapmalısınız.');
  }
  return uid;
}

export async function ensureUserProfile(user: User): Promise<UserPublic> {
  const uid = user.uid;
  const publicRef = doc(db, 'users_public', uid);
  const privateRef = doc(db, 'users_private', uid);

  let existingSnap;
  try {
    existingSnap = await getDoc(publicRef);
  } catch (err) {
    handleFirestoreError(err, OperationType.GET, publicRef.path);
  }

  const isAdmin = isCurrentUserAdmin(user);

  if (existingSnap.exists()) {
    const profile = mapUserPublic(existingSnap.id, existingSnap.data() as Record<string, unknown>);
    const shouldVerify = isAdmin || isVerifiedUsername(profile.username);
    if (shouldVerify && !profile.isVerified) {
      try {
        await updateDoc(publicRef, {
          isVerified: true,
          updatedAt: serverTimestamp(),
        });
        return { ...profile, isVerified: true };
      } catch {
        return { ...profile, isVerified: true };
      }
    }
    return shouldVerify ? { ...profile, isVerified: true } : profile;
  }

  const rawName = (user.displayName ?? '').trim() || 'SocialWiew Kullanıcısı';
  const displayName = rawName.slice(0, 50);
  const emailPrefixRaw = (user.email ?? '').split('@')[0]?.toLowerCase().replace(/[^a-z0-9_]/g, '') ?? '';
  const fallbackBase = emailPrefixRaw.length >= 3 ? emailPrefixRaw.slice(0, 18) : `viber_${uid.slice(0, 6).toLowerCase()}`;
  const fallbackHandle = fallbackBase.padEnd(3, 'x').slice(0, 30);
  const avatarUrl = (user.photoURL ?? '').slice(0, 1000);
  const initialVerified = isAdmin || isVerifiedUsername(fallbackHandle);

  const batch = writeBatch(db);
  batch.set(publicRef, {
    userId: uid,
    displayName,
    username: fallbackHandle,
    bio: '',
    avatarUrl,
    isVerified: initialVerified,
    followersCount: 0,
    followingCount: 0,
    videosCount: 0,
    isPublic: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  batch.set(privateRef, {
    userId: uid,
    email: (user.email ?? '').slice(0, 200),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });

  try {
    await batch.commit();
  } catch (err) {
    handleFirestoreError(err, OperationType.CREATE, publicRef.path);
  }

  return {
    id: uid,
    userId: uid,
    displayName,
    username: fallbackHandle,
    bio: '',
    avatarUrl,
    isVerified: initialVerified,
    followersCount: 0,
    followingCount: 0,
    videosCount: 0,
    isPublic: true,
    createdAtMs: Date.now(),
    updatedAtMs: Date.now(),
  };
}

export function subscribePublicVideos(
  onData: (videos: VideoPost[]) => void,
  onError: (err: Error) => void
) {
  const path = 'videos';
  const q = query(collection(db, path), where('visibility', '==', 'public'));
  return onSnapshot(
    q,
    (snapshot) => {
      const list = snapshot.docs
        .map((d) => mapVideoPost(d.id, d.data() as Record<string, unknown>))
        .sort((a, b) => b.createdAtMs - a.createdAtMs);
      onData(list);
    },
    (err) => {
      try {
        handleFirestoreError(err, OperationType.LIST, path);
      } catch (wrapped) {
        onError(wrapped instanceof Error ? wrapped : new Error(String(wrapped)));
      }
    }
  );
}

export function subscribeCreators(
  onData: (creators: UserPublic[]) => void,
  onError: (err: Error) => void
) {
  const path = 'users_public';
  const q = query(collection(db, path), where('isPublic', '==', true));
  return onSnapshot(
    q,
    (snapshot) => {
      const list = snapshot.docs
        .map((d) => mapUserPublic(d.id, d.data() as Record<string, unknown>))
        .sort((a, b) => b.followersCount - a.followersCount);
      onData(list);
    },
    (err) => {
      try {
        handleFirestoreError(err, OperationType.LIST, path);
      } catch (wrapped) {
        onError(wrapped instanceof Error ? wrapped : new Error(String(wrapped)));
      }
    }
  );
}

export function subscribeFollowingIds(userId: string, onData: (ids: Set<string>) => void) {
  const path = 'follows';
  const q = query(collection(db, path), where('followerId', '==', userId));
  return onSnapshot(
    q,
    (snapshot) => {
      onData(new Set(snapshot.docs.map((d) => String(d.data().followingId ?? '')).filter(Boolean)));
    },
    (err) => {
      try {
        handleFirestoreError(err, OperationType.LIST, path);
      } catch {
        // Ignore teardown errors
      }
    }
  );
}

export function subscribeLikedVideoIds(userId: string, onData: (ids: Set<string>) => void) {
  const path = 'video_likes';
  const q = query(collection(db, path), where('userId', '==', userId));
  return onSnapshot(
    q,
    (snapshot) => {
      onData(new Set(snapshot.docs.map((d) => String(d.data().videoId ?? '')).filter(Boolean)));
    },
    (err) => {
      try {
        handleFirestoreError(err, OperationType.LIST, path);
      } catch {
        // Ignore teardown errors
      }
    }
  );
}

export function subscribeComments(videoId: string, onData: (comments: VideoComment[]) => void) {
  const path = `videos/${videoId}/comments`;
  const q = query(
    collection(db, 'videos', videoId, 'comments'),
    where('videoId', '==', videoId),
    where('visibility', '==', 'public')
  );
  return onSnapshot(
    q,
    (snapshot) => {
      const list = snapshot.docs
        .map((d) => mapVideoComment(d.id, d.data() as Record<string, unknown>))
        .sort((a, b) => b.createdAtMs - a.createdAtMs);
      onData(list);
    },
    (err) => {
      try {
        handleFirestoreError(err, OperationType.LIST, path);
      } catch {
        // Ignore teardown errors
      }
    }
  );
}

export function subscribeSharedUpdateRepo(onData: (repo: string) => void) {
  const ref = doc(db, 'app_config', 'updates');
  return onSnapshot(
    ref,
    (snapshot) => {
      const data = snapshot.data() as { githubRepo?: string } | undefined;
      onData((data?.githubRepo ?? '').trim());
    },
    () => {
      onData('');
    }
  );
}

export async function updateProfile(displayName: string, username: string, bio: string): Promise<void> {
  const uid = requireUserId();
  const cleanDisplayName = displayName.trim().slice(0, 50);
  const cleanUsername = username.trim().toLowerCase().replace(/[^a-z0-9_.]/g, '').slice(0, 30);
  if (!cleanDisplayName) throw new Error('İsim boş olamaz.');
  if (cleanUsername.length < 3) throw new Error('Kullanıcı adı en az 3 karakter olmalıdır.');

  const ref = doc(db, 'users_public', uid);
  try {
    await updateDoc(ref, {
      displayName: cleanDisplayName,
      username: cleanUsername,
      bio: bio.trim().slice(0, 200),
      updatedAt: serverTimestamp(),
    });
  } catch (err) {
    handleFirestoreError(err, OperationType.UPDATE, ref.path);
  }
}

export async function setUserVerified(targetUserId: string, verified: boolean): Promise<void> {
  requireUserId();
  if (!isCurrentUserAdmin(auth.currentUser)) {
    throw new Error('Yalnızca yetkili admin mavi tik verebilir.');
  }
  const ref = doc(db, 'users_public', targetUserId);
  try {
    await updateDoc(ref, {
      isVerified: verified,
      updatedAt: serverTimestamp(),
    });
  } catch (err) {
    handleFirestoreError(err, OperationType.UPDATE, ref.path);
  }
}

export async function saveSharedUpdateRepo(repoSlug: string): Promise<void> {
  const uid = requireUserId();
  if (!isCurrentUserAdmin(auth.currentUser)) {
    throw new Error('Genel güncelleme deposunu yalnızca admin güncelleyebilir.');
  }
  const ref = doc(db, 'app_config', 'updates');
  const batch = writeBatch(db);
  batch.set(ref, {
    githubRepo: repoSlug.trim().slice(0, 160),
    updatedBy: uid,
    updatedAt: serverTimestamp(),
  });
  try {
    await batch.commit();
  } catch (err) {
    handleFirestoreError(err, OperationType.WRITE, ref.path);
  }
}

export async function toggleFollow(targetUserId: string, currentlyFollowing: boolean): Promise<void> {
  const uid = requireUserId();
  if (uid === targetUserId) throw new Error('Kendinizi takip edemezsiniz.');

  const followId = `${uid}_${targetUserId}`;
  const followRef = doc(db, 'follows', followId);
  const myRef = doc(db, 'users_public', uid);
  const targetRef = doc(db, 'users_public', targetUserId);

  const batch = writeBatch(db);
  if (currentlyFollowing) {
    batch.delete(followRef);
    batch.update(myRef, { followingCount: increment(-1), updatedAt: serverTimestamp() });
    batch.update(targetRef, { followersCount: increment(-1), updatedAt: serverTimestamp() });
  } else {
    batch.set(followRef, { followerId: uid, followingId: targetUserId, createdAt: serverTimestamp() });
    batch.update(myRef, { followingCount: increment(1), updatedAt: serverTimestamp() });
    batch.update(targetRef, { followersCount: increment(1), updatedAt: serverTimestamp() });
  }

  try {
    await batch.commit();
  } catch (err) {
    handleFirestoreError(err, OperationType.WRITE, followRef.path);
  }
}

export async function toggleLike(videoId: string, currentlyLiked: boolean): Promise<void> {
  const uid = requireUserId();
  const likeId = `${videoId}_${uid}`;
  const likeRef = doc(db, 'video_likes', likeId);
  const videoRef = doc(db, 'videos', videoId);

  const batch = writeBatch(db);
  if (currentlyLiked) {
    batch.delete(likeRef);
    batch.update(videoRef, { likesCount: increment(-1), updatedAt: serverTimestamp() });
  } else {
    batch.set(likeRef, { videoId, userId: uid, createdAt: serverTimestamp() });
    batch.update(videoRef, { likesCount: increment(1), updatedAt: serverTimestamp() });
  }

  try {
    await batch.commit();
  } catch (err) {
    handleFirestoreError(err, OperationType.WRITE, likeRef.path);
  }
}

function uint8ToBase64(bytes: Uint8Array): string {
  let binary = '';
  const step = 0x8000;
  for (let i = 0; i < bytes.length; i += step) {
    const slice = bytes.subarray(i, Math.min(i + step, bytes.length));
    binary += String.fromCharCode(...slice);
  }
  return btoa(binary);
}

function base64ToUint8(b64: string): Uint8Array {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i += 1) {
    out[i] = bin.charCodeAt(i);
  }
  return out;
}

export async function publishVideo(params: {
  author: UserPublic;
  caption: string;
  videoUrl: string;
  file?: File | null;
  thumbnailData: string | null;
  durationMs: number;
  tags: string[];
}): Promise<string> {
  const uid = requireUserId();
  const cleanUrl = params.videoUrl.trim().slice(0, 2000);

  const encodedChunks: string[] = [];
  if (params.file) {
    if (params.file.size > MAX_CLOUD_VIDEO_BYTES) {
      throw new Error('Video boyutu en fazla 15 MB olabilir. Lütfen daha kısa bir video seçin.');
    }
    const buf = new Uint8Array(await params.file.arrayBuffer());
    for (let offset = 0; offset < buf.length; offset += CHUNK_RAW_BYTES) {
      const slice = buf.subarray(offset, Math.min(offset + CHUNK_RAW_BYTES, buf.length));
      encodedChunks.push(uint8ToBase64(slice));
    }
    if (encodedChunks.length > MAX_CHUNKS) {
      throw new Error('Video parça sınırı aşıldı. Lütfen daha kısa bir video seçin.');
    }
  }

  if (!cleanUrl && encodedChunks.length === 0) {
    throw new Error('Lütfen bir video seçin veya geçerli bir video bağlantısı girin.');
  }

  const videoRef = doc(collection(db, 'videos'));
  const profileRef = doc(db, 'users_public', uid);
  const isSingleBatch = encodedChunks.length <= CHUNKS_PER_BATCH;

  const payload: Record<string, unknown> = {
    userId: uid,
    authorName: (params.author.displayName || 'Kullanıcı').slice(0, 60),
    authorUsername: (params.author.username || 'viber').slice(0, 40),
    authorVerified:
      params.author.isVerified ||
      isCurrentUserAdmin(auth.currentUser) ||
      isVerifiedUsername(params.author.username),
    caption: params.caption.trim().slice(0, 300),
    videoUrl: cleanUrl,
    chunkCount: isSingleBatch ? encodedChunks.length : 0,
    durationMs: Math.max(0, Math.round(params.durationMs)),
    tags: params.tags
      .map((t) => t.trim().slice(0, 24))
      .filter(Boolean)
      .slice(0, 5),
    visibility: 'public',
    likesCount: 0,
    commentsCount: 0,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };

  if (params.author.avatarUrl) {
    payload.authorAvatarUrl = params.author.avatarUrl.slice(0, 1000);
  }
  if (params.file?.name) {
    payload.localFileName = params.file.name.slice(0, 200);
  }
  if (params.thumbnailData) {
    payload.thumbnailData = params.thumbnailData.slice(0, 150_000);
  }

  const firstBatchCount = Math.min(encodedChunks.length, CHUNKS_PER_BATCH);
  const batch = writeBatch(db);
  batch.set(videoRef, payload);
  batch.update(profileRef, { videosCount: increment(1), updatedAt: serverTimestamp() });

  for (let i = 0; i < firstBatchCount; i += 1) {
    const chunkRef = doc(db, 'videos', videoRef.id, 'media_chunks', `chunk_${i}`);
    batch.set(chunkRef, {
      videoId: videoRef.id,
      userId: uid,
      chunkIndex: i,
      data: encodedChunks[i],
      visibility: 'public',
      createdAt: serverTimestamp(),
    });
  }

  try {
    await batch.commit();
  } catch (err) {
    handleFirestoreError(err, OperationType.CREATE, videoRef.path);
  }

  if (!isSingleBatch) {
    for (let idx = firstBatchCount; idx < encodedChunks.length; idx += CHUNKS_PER_BATCH) {
      const end = Math.min(idx + CHUNKS_PER_BATCH, encodedChunks.length);
      const nextBatch = writeBatch(db);
      for (let c = idx; c < end; c += 1) {
        const chunkRef = doc(db, 'videos', videoRef.id, 'media_chunks', `chunk_${c}`);
        nextBatch.set(chunkRef, {
          videoId: videoRef.id,
          userId: uid,
          chunkIndex: c,
          data: encodedChunks[c],
          visibility: 'public',
          createdAt: serverTimestamp(),
        });
      }
      try {
        await nextBatch.commit();
      } catch (err) {
        handleFirestoreError(err, OperationType.CREATE, `${videoRef.path}/media_chunks`);
      }
    }

    try {
      await updateDoc(videoRef, {
        chunkCount: encodedChunks.length,
        updatedAt: serverTimestamp(),
      });
    } catch (err) {
      handleFirestoreError(err, OperationType.UPDATE, videoRef.path);
    }
  }

  return videoRef.id;
}

export async function downloadCloudVideoBlobUrl(
  videoId: string,
  expectedChunkCount = 0
): Promise<string> {
  requireUserId();
  const cached = cloudBlobCache.get(videoId);
  if (cached) return cached;

  const path = `videos/${videoId}/media_chunks`;
  const q = query(
    collection(db, 'videos', videoId, 'media_chunks'),
    where('videoId', '==', videoId),
    where('visibility', '==', 'public')
  );

  let snap;
  try {
    snap = await getDocs(q);
  } catch (err) {
    handleFirestoreError(err, OperationType.LIST, path);
  }

  const chunks = snap.docs
    .map((d) => ({
      chunkIndex: Number(d.data().chunkIndex ?? 0),
      data: String(d.data().data ?? ''),
    }))
    .sort((a, b) => a.chunkIndex - b.chunkIndex);

  if (chunks.length === 0) {
    throw new Error('Video bulut parçaları bulunamadı.');
  }
  if (expectedChunkCount > 0 && chunks.length < expectedChunkCount) {
    throw new Error(`Video parçaları eksik (${chunks.length}/${expectedChunkCount}).`);
  }

  const byteArrays = chunks.map((c) => base64ToUint8(c.data));
  const totalBytes = byteArrays.reduce((sum, arr) => sum + arr.length, 0);
  const merged = new Uint8Array(totalBytes);
  let offset = 0;
  for (const arr of byteArrays) {
    merged.set(arr, offset);
    offset += arr.length;
  }

  const blob = new Blob([merged], { type: 'video/mp4' });
  const blobUrl = URL.createObjectURL(blob);
  cloudBlobCache.set(videoId, blobUrl);
  return blobUrl;
}

export async function deleteVideo(videoId: string): Promise<void> {
  const uid = requireUserId();
  const videoRef = doc(db, 'videos', videoId);
  const profileRef = doc(db, 'users_public', uid);
  const batch = writeBatch(db);
  batch.delete(videoRef);
  batch.update(profileRef, { videosCount: increment(-1), updatedAt: serverTimestamp() });
  try {
    await batch.commit();
  } catch (err) {
    handleFirestoreError(err, OperationType.DELETE, videoRef.path);
  }
}

export async function addComment(params: {
  videoId: string;
  author: UserPublic;
  text: string;
  gifUrl?: string | null;
  gifPreviewUrl?: string | null;
}): Promise<string> {
  const uid = requireUserId();
  const cleanText = params.text.trim().slice(0, 500);
  const cleanGifUrl = (params.gifUrl ?? '').trim().slice(0, 1000);
  if (!cleanText && !cleanGifUrl) {
    throw new Error('Yorum metni veya GIF seçmelisiniz.');
  }

  const videoRef = doc(db, 'videos', params.videoId);
  const commentRef = doc(collection(db, 'videos', params.videoId, 'comments'));

  const payload: Record<string, unknown> = {
    videoId: params.videoId,
    userId: uid,
    authorName: (params.author.displayName || 'Kullanıcı').slice(0, 60),
    authorUsername: (params.author.username || 'viber').slice(0, 40),
    authorVerified:
      params.author.isVerified ||
      isCurrentUserAdmin(auth.currentUser) ||
      isVerifiedUsername(params.author.username),
    text: cleanText,
    visibility: 'public',
    createdAt: serverTimestamp(),
  };

  if (params.author.avatarUrl) {
    payload.authorAvatarUrl = params.author.avatarUrl.slice(0, 1000);
  }
  if (cleanGifUrl) payload.gifUrl = cleanGifUrl;
  if (params.gifPreviewUrl?.trim()) {
    payload.gifPreviewUrl = params.gifPreviewUrl.trim().slice(0, 1000);
  }

  const batch = writeBatch(db);
  batch.set(commentRef, payload);
  batch.update(videoRef, { commentsCount: increment(1), updatedAt: serverTimestamp() });

  try {
    await batch.commit();
  } catch (err) {
    handleFirestoreError(err, OperationType.CREATE, commentRef.path);
  }

  return commentRef.id;
}

export async function deleteComment(videoId: string, commentId: string): Promise<void> {
  requireUserId();
  const videoRef = doc(db, 'videos', videoId);
  const commentRef = doc(db, 'videos', videoId, 'comments', commentId);
  const batch = writeBatch(db);
  batch.delete(commentRef);
  batch.update(videoRef, { commentsCount: increment(-1), updatedAt: serverTimestamp() });
  try {
    await batch.commit();
  } catch (err) {
    handleFirestoreError(err, OperationType.DELETE, commentRef.path);
  }
}
