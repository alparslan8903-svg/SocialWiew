import Foundation

private struct GitHubReleaseDTO: Decodable {
    let tag_name: String?
    let name: String?
    let body: String?
    let html_url: String?
    let published_at: String?
    let draft: Bool?
}

private struct GitHubTagDTO: Decodable {
    let name: String?
}

final class GitHubUpdateService {
    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func checkForUpdate(rawRepoInput: String, currentVersion: String) async throws -> AppUpdateInfo {
        guard let slug = Self.parseGitHubRepoSlug(rawRepoInput) else {
            throw NSError(domain: "GitHubUpdateService", code: -1, userInfo: [
                NSLocalizedDescriptionKey: "Geçerli bir GitHub deposu girin (ör. kullanici/SocialWiew)."
            ])
        }

        if let release = try await fetchLatestRelease(slug) ?? fetchFirstListedRelease(slug) {
            let tag = (release.tag_name?.isEmpty == false ? release.tag_name : release.name) ?? "1.0"
            let title = release.name?.trimmingCharacters(in: .whitespacesAndNewlines)
            let notes = release.body?.trimmingCharacters(in: .whitespacesAndNewlines)

            return AppUpdateInfo(
                repoSlug: slug,
                tagName: tag,
                title: (title?.isEmpty == false ? title! : "SocialWiew \(tag)"),
                releaseNotes: (notes?.isEmpty == false ? notes! : "Yeni sürüm GitHub üzerinde yayınlandı."),
                htmlUrl: (release.html_url?.isEmpty == false ? release.html_url! : "https://github.com/\(slug)/releases"),
                publishedAt: release.published_at ?? "",
                currentVersion: currentVersion,
                isNewerAvailable: Self.isNewerVersion(remoteTag: tag, currentVersion: currentVersion)
            )
        }

        guard let tag = try await fetchFirstTag(slug), let tagName = tag.name, !tagName.isEmpty else {
            throw NSError(domain: "GitHubUpdateService", code: 404, userInfo: [
                NSLocalizedDescriptionKey: "GitHub deposunda henüz yayınlanmış bir sürüm (Release/Tag) bulunamadı."
            ])
        }

        return AppUpdateInfo(
            repoSlug: slug,
            tagName: tagName,
            title: "SocialWiew \(tagName)",
            releaseNotes: "GitHub'da \(tagName) etiketi yayınlandı. Değişiklikleri GitHub üzerinden inceleyebilirsiniz.",
            htmlUrl: "https://github.com/\(slug)/releases/tag/\(tagName)",
            publishedAt: "",
            currentVersion: currentVersion,
            isNewerAvailable: Self.isNewerVersion(remoteTag: tagName, currentVersion: currentVersion)
        )
    }

    private func fetchLatestRelease(_ slug: String) async throws -> GitHubReleaseDTO? {
        guard let data = try await executeGet("https://api.github.com/repos/\(slug)/releases/latest", allow404: true) else {
            return nil
        }
        let release = try? JSONDecoder().decode(GitHubReleaseDTO.self, from: data)
        guard let release, release.draft != true, release.tag_name?.isEmpty == false else {
            return nil
        }
        return release
    }

    private func fetchFirstListedRelease(_ slug: String) async throws -> GitHubReleaseDTO? {
        guard let data = try await executeGet("https://api.github.com/repos/\(slug)/releases?per_page=3", allow404: true) else {
            return nil
        }
        let list = (try? JSONDecoder().decode([GitHubReleaseDTO].self, from: data)) ?? []
        return list.first(where: { $0.draft != true && $0.tag_name?.isEmpty == false })
    }

    private func fetchFirstTag(_ slug: String) async throws -> GitHubTagDTO? {
        guard let data = try await executeGet("https://api.github.com/repos/\(slug)/tags?per_page=3", allow404: false) else {
            return nil
        }
        let list = (try? JSONDecoder().decode([GitHubTagDTO].self, from: data)) ?? []
        return list.first(where: { $0.name?.isEmpty == false })
    }

    private func executeGet(_ urlString: String, allow404: Bool) async throws -> Data? {
        guard let url = URL(string: urlString) else { return nil }
        var request = URLRequest(url: url, timeoutInterval: 12)
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        request.setValue("SocialWiew-iOS", forHTTPHeaderField: "User-Agent")

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { return nil }

        if http.statusCode == 404 && allow404 { return nil }
        if http.statusCode == 404 {
            throw NSError(domain: "GitHubUpdateService", code: 404, userInfo: [
                NSLocalizedDescriptionKey: "GitHub deposu bulunamadı veya herkese açık değil."
            ])
        }
        if http.statusCode == 403 || http.statusCode == 429 {
            throw NSError(domain: "GitHubUpdateService", code: http.statusCode, userInfo: [
                NSLocalizedDescriptionKey: "GitHub API istek sınırına ulaşıldı, lütfen biraz sonra tekrar deneyin."
            ])
        }
        guard (200..<300).contains(http.statusCode) else {
            throw NSError(domain: "GitHubUpdateService", code: http.statusCode, userInfo: [
                NSLocalizedDescriptionKey: "GitHub güncellemeleri alınamadı (HTTP \(http.statusCode))."
            ])
        }
        return data
    }

    static func parseGitHubRepoSlug(_ rawInput: String) -> String? {
        var trimmed = rawInput.trimmingCharacters(in: .whitespacesAndNewlines)
        while trimmed.hasSuffix("/") { trimmed.removeLast() }
        if trimmed.lowercased().hasSuffix(".git") {
            trimmed = String(trimmed.dropLast(4))
        }
        guard !trimmed.isEmpty else { return nil }

        let lower = trimmed.lowercased()
        var pathPart = trimmed
        if lower.hasPrefix("https://github.com/") {
            pathPart = String(trimmed.dropFirst("https://github.com/".count))
        } else if lower.hasPrefix("http://github.com/") {
            pathPart = String(trimmed.dropFirst("http://github.com/".count))
        } else if lower.hasPrefix("github.com/") {
            pathPart = String(trimmed.dropFirst("github.com/".count))
        } else if lower.hasPrefix("http://") || lower.hasPrefix("https://") {
            return nil
        }

        let segments = pathPart.split(separator: "/").map(String.init).filter { !$0.isEmpty }
        guard segments.count >= 2 else { return nil }
        let owner = segments[0]
        let repo = segments[1]
        let regex = try? NSRegularExpression(pattern: "^[A-Za-z0-9_.-]+$")
        let ownerOk = regex?.firstMatch(in: owner, range: NSRange(owner.startIndex..<owner.endIndex, in: owner)) != nil
        let repoOk = regex?.firstMatch(in: repo, range: NSRange(repo.startIndex..<repo.endIndex, in: repo)) != nil
        guard ownerOk && repoOk else { return nil }
        return "\(owner)/\(repo)"
    }

    static func isNewerVersion(remoteTag: String, currentVersion: String) -> Bool {
        let remoteNums = extractNumericParts(remoteTag)
        let currentNums = extractNumericParts(currentVersion)
        if remoteNums.isEmpty { return false }
        if currentNums.isEmpty { return true }

        let maxLen = max(remoteNums.count, currentNums.count)
        for idx in 0..<maxLen {
            let r = idx < remoteNums.count ? remoteNums[idx] : 0
            let c = idx < currentNums.count ? currentNums[idx] : 0
            if r > c { return true }
            if r < c { return false }
        }
        let remoteHasPre = remoteTag.trimmingCharacters(in: .whitespacesAndNewlines).contains("-")
        let currentHasPre = currentVersion.trimmingCharacters(in: .whitespacesAndNewlines).contains("-")
        return !remoteHasPre && currentHasPre
    }

    private static func extractNumericParts(_ version: String) -> [Int] {
        var core = version.trimmingCharacters(in: .whitespacesAndNewlines)
        if core.hasPrefix("v") || core.hasPrefix("V") {
            core.removeFirst()
        }
        core = String(core.split(separator: "-", maxSplits: 1).first ?? "")
        core = String(core.split(separator: "+", maxSplits: 1).first ?? "")
        guard !core.isEmpty else { return [] }
        return core.split(separator: ".").compactMap { part in
            let digits = part.filter(\.isNumber)
            return digits.isEmpty ? nil : Int(digits)
        }
    }
}
