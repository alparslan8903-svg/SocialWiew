export type ThemeMode = 'dark' | 'light' | 'system';

export interface UserPublic {
  id: string;
  userId: string;
  displayName: string;
  username: string;
  bio: string;
  avatarUrl: string;
  isVerified: boolean;
  followersCount: number;
  followingCount: number;
  videosCount: number;
  isPublic: boolean;
  createdAtMs: number;
  updatedAtMs: number;
}

export interface VideoPost {
  id: string;
  userId: string;
  authorName: string;
  authorUsername: string;
  authorAvatarUrl: string;
  authorVerified: boolean;
  caption: string;
  videoUrl: string;
  localFileName: string;
  thumbnailData: string;
  chunkCount: number;
  durationMs: number;
  tags: string[];
  visibility: string;
  likesCount: number;
  commentsCount: number;
  createdAtMs: number;
  updatedAtMs: number;
}

export interface VideoComment {
  id: string;
  videoId: string;
  userId: string;
  authorName: string;
  authorUsername: string;
  authorAvatarUrl: string;
  authorVerified: boolean;
  text: string;
  gifUrl: string;
  gifPreviewUrl: string;
  visibility: string;
  createdAtMs: number;
}

export interface TenorGifItem {
  id: string;
  title: string;
  fullUrl: string;
  previewUrl: string;
}

export interface AppUpdateInfo {
  repoSlug: string;
  tagName: string;
  title: string;
  releaseNotes: string;
  htmlUrl: string;
  apkDownloadUrl: string | null;
  publishedAt: string;
  currentVersion: string;
  isNewerAvailable: boolean;
}
