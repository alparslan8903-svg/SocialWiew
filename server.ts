import express from 'express';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = 3000;
const HOST = '0.0.0.0';

const MOBILE_USER_AGENT =
  'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36';
const TENOR_GIF_REGEX =
  /https:\/\/media[0-9]*\.tenor\.com\/(?:m\/)?([A-Za-z0-9_-]+)\/([A-Za-z0-9_.-]+)\.gif/g;
const SEGMENT_REGEX = /^[A-Za-z0-9_.-]+$/;

function foldTurkishChars(input: string): string {
  return input
    .replace(/ç/g, 'c')
    .replace(/ğ/g, 'g')
    .replace(/ı/g, 'i')
    .replace(/ö/g, 'o')
    .replace(/ş/g, 's')
    .replace(/ü/g, 'u');
}

function buildTenorUrl(query: string): string {
  const clean = query.trim();
  if (!clean) return 'https://tenor.com/';
  const folded = foldTurkishChars(clean.toLowerCase())
    .replace(/[^a-z0-9\s-]+/g, ' ')
    .trim()
    .replace(/\s+/g, '-');
  const slug = folded || encodeURIComponent(clean);
  return `https://tenor.com/search/${slug}-gifs`;
}

function parseTenorGifsFromHtml(html: string, limit = 24) {
  if (!html) return [];
  const variantsByAsset = new Map<string, Array<{ url: string; slug: string }>>();

  for (const match of html.matchAll(TENOR_GIF_REGEX)) {
    const fullMatchedUrl = match[0];
    const rawAssetId = match[1];
    const slugName = match[2];
    const canonicalId =
      rawAssetId.length > 6 && rawAssetId.slice(-6).startsWith('AAAA')
        ? rawAssetId.slice(0, -6)
        : rawAssetId;

    const existing = variantsByAsset.get(canonicalId) ?? [];
    if (!existing.some((item) => item.url === fullMatchedUrl)) {
      existing.push({ url: fullMatchedUrl, slug: slugName });
      variantsByAsset.set(canonicalId, existing);
    }
  }

  return Array.from(variantsByAsset.entries())
    .slice(0, limit)
    .map(([assetId, variants]) => {
      const urls = variants.map((v) => v.url);
      const preview =
        urls.find((u) => u.includes('AAAAM/')) ??
        urls.find((u) => u.includes('AAAAd/')) ??
        urls[0];
      const full =
        urls.find((u) => u.includes('AAAAC/')) ??
        urls.find((u) => u.includes('AAAAd/')) ??
        preview;
      const title =
        variants[0].slug.replace(/[-_]/g, ' ').trim() || 'Tenor GIF';
      return {
        id: assetId,
        title,
        fullUrl: full,
        previewUrl: preview,
      };
    });
}

function parseGitHubRepoSlug(rawInput: string): string | null {
  const trimmed = rawInput
    .trim()
    .replace(/\/+$/, '')
    .replace(/\.git$/i, '');
  if (!trimmed) return null;

  let pathPart = trimmed;
  const lower = trimmed.toLowerCase();
  if (lower.startsWith('https://github.com/')) {
    pathPart = trimmed.slice('https://github.com/'.length);
  } else if (lower.startsWith('http://github.com/')) {
    pathPart = trimmed.slice('http://github.com/'.length);
  } else if (lower.startsWith('github.com/')) {
    pathPart = trimmed.slice('github.com/'.length);
  } else if (lower.startsWith('http://') || lower.startsWith('https://')) {
    return null;
  }

  const segments = pathPart.split('/').filter(Boolean);
  if (segments.length < 2) return null;
  const [owner, repo] = segments;
  if (!SEGMENT_REGEX.test(owner) || !SEGMENT_REGEX.test(repo)) return null;
  return `${owner}/${repo}`;
}

function extractNumericParts(version: string): number[] {
  const core = version
    .trim()
    .replace(/^[vV]/, '')
    .split('-')[0]
    .split('+')[0];
  if (!core) return [];
  return core
    .split('.')
    .map((part) => {
      const digits = part.replace(/\D/g, '');
      return digits ? Number.parseInt(digits, 10) : Number.NaN;
    })
    .filter((n) => !Number.isNaN(n));
}

function isNewerVersion(remoteTag: string, currentVersion: string): boolean {
  const remoteNums = extractNumericParts(remoteTag);
  const currentNums = extractNumericParts(currentVersion);
  if (remoteNums.length === 0) return false;
  if (currentNums.length === 0) return true;

  const maxLen = Math.max(remoteNums.length, currentNums.length);
  for (let i = 0; i < maxLen; i += 1) {
    const r = remoteNums[i] ?? 0;
    const c = currentNums[i] ?? 0;
    if (r > c) return true;
    if (r < c) return false;
  }

  const remoteHasPre = remoteTag.trim().includes('-');
  const currentHasPre = currentVersion.trim().includes('-');
  return !remoteHasPre && currentHasPre;
}

async function fetchGitHubJson(url: string, allow404: boolean) {
  const res = await fetch(url, {
    headers: {
      Accept: 'application/vnd.github+json',
      'User-Agent': 'SocialWiew-Web',
    },
    signal: AbortSignal.timeout(10_000),
  });
  if (res.status === 404 && allow404) return null;
  if (res.status === 404) {
    throw new Error('GitHub deposu bulunamadı veya herkese açık değil.');
  }
  if (res.status === 403 || res.status === 429) {
    throw new Error('GitHub API istek sınırına ulaşıldı, lütfen biraz sonra tekrar deneyin.');
  }
  if (!res.ok) {
    throw new Error(`GitHub güncellemeleri alınamadı (HTTP ${res.status}).`);
  }
  return res.json();
}

async function startServer() {
  const app = express();
  app.use(express.json());

  app.get('/api/health', (_req, res) => {
    res.json({ status: 'ok' });
  });

  app.get('/api/tenor', async (req, res) => {
    try {
      const query = typeof req.query.q === 'string' ? req.query.q : '';
      const targetUrl = buildTenorUrl(query);
      const response = await fetch(targetUrl, {
        headers: {
          'User-Agent': MOBILE_USER_AGENT,
          'Accept-Language': 'tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7',
        },
        signal: AbortSignal.timeout(12_000),
      });
      if (!response.ok) {
        res.status(502).json({
          error: `Tenor GIF'leri yüklenemedi (HTTP ${response.status}).`,
        });
        return;
      }
      const html = await response.text();
      const gifs = parseTenorGifsFromHtml(html, 24);
      res.json({ gifs });
    } catch (err) {
      res.status(502).json({
        error: err instanceof Error ? err.message : "Tenor GIF'leri yüklenemedi.",
      });
    }
  });

  app.get('/api/github/releases', async (req, res) => {
    try {
      const rawRepo = typeof req.query.repo === 'string' ? req.query.repo : '';
      const currentVersion =
        typeof req.query.currentVersion === 'string' && req.query.currentVersion.trim()
          ? req.query.currentVersion.trim()
          : '1.0';
      const slug = parseGitHubRepoSlug(rawRepo);
      if (!slug) {
        res.status(400).json({
          error: 'Geçerli bir GitHub deposu girin (ör. kullanici/SocialWiew).',
        });
        return;
      }

      interface GitHubRelease {
        tag_name?: string;
        name?: string;
        body?: string;
        html_url?: string;
        published_at?: string;
        draft?: boolean;
        assets?: Array<{ name?: string; browser_download_url?: string }>;
      }

      let latestRelease = (await fetchGitHubJson(
        `https://api.github.com/repos/${slug}/releases/latest`,
        true
      )) as GitHubRelease | null;

      if (!latestRelease || latestRelease.draft || !latestRelease.tag_name) {
        const list = (await fetchGitHubJson(
          `https://api.github.com/repos/${slug}/releases?per_page=3`,
          true
        )) as GitHubRelease[] | null;
        latestRelease =
          (list ?? []).find((r) => !r.draft && Boolean(r.tag_name)) ?? null;
      }

      if (latestRelease && !latestRelease.draft && latestRelease.tag_name) {
        const tag = latestRelease.tag_name || latestRelease.name || '1.0';
        const apkAssetUrl =
          latestRelease.assets?.find(
            (a) =>
              a.name?.toLowerCase().endsWith('.apk') &&
              Boolean(a.browser_download_url)
          )?.browser_download_url ?? null;

        res.json({
          repoSlug: slug,
          tagName: tag,
          title: latestRelease.name?.trim() || `SocialWiew ${tag}`,
          releaseNotes:
            latestRelease.body?.trim() || 'Yeni sürüm GitHub üzerinde yayınlandı.',
          htmlUrl:
            latestRelease.html_url || `https://github.com/${slug}/releases`,
          apkDownloadUrl: apkAssetUrl,
          publishedAt: latestRelease.published_at ?? '',
          currentVersion,
          isNewerAvailable: isNewerVersion(tag, currentVersion),
        });
        return;
      }

      const tags = (await fetchGitHubJson(
        `https://api.github.com/repos/${slug}/tags?per_page=3`,
        false
      )) as Array<{ name?: string }> | null;
      const firstTag = (tags ?? []).find((t) => Boolean(t.name));
      if (!firstTag?.name) {
        res.status(404).json({
          error:
            'GitHub deposunda henüz yayınlanmış bir sürüm (Release/Tag) bulunamadı.',
        });
        return;
      }

      res.json({
        repoSlug: slug,
        tagName: firstTag.name,
        title: `SocialWiew ${firstTag.name}`,
        releaseNotes: `GitHub'da ${firstTag.name} etiketi yayınlandı. Değişiklikleri GitHub üzerinden inceleyebilirsiniz.`,
        htmlUrl: `https://github.com/${slug}/releases/tag/${firstTag.name}`,
        apkDownloadUrl: null,
        publishedAt: '',
        currentVersion,
        isNewerAvailable: isNewerVersion(firstTag.name, currentVersion),
      });
    } catch (err) {
      res.status(502).json({
        error:
          err instanceof Error ? err.message : 'GitHub güncellemeleri alınamadı.',
      });
    }
  });

  if (process.env.NODE_ENV !== 'production') {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: { middlewareMode: true, hmr: false },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distDir = path.join(__dirname, 'dist');
    app.use(express.static(distDir));
    app.get('/{*splat}', (_req, res) => {
      res.sendFile(path.join(distDir, 'index.html'));
    });
  }

  app.listen(PORT, HOST, () => {
    console.log(`SocialWiew server listening on http://${HOST}:${PORT}`);
  });
}

startServer().catch((err) => {
  console.error('Failed to start server:', err);
  process.exit(1);
});
