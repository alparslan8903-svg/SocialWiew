import Foundation

final class TenorService {
    private let session: URLSession
    private static let mobileUserAgent =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

    init(session: URLSession = .shared) {
        self.session = session
    }

    func loadGifs(query: String) async throws -> [TenorGifItem] {
        guard let url = URL(string: buildTenorUrl(query: query)) else {
            throw NSError(domain: "TenorService", code: -1, userInfo: [
                NSLocalizedDescriptionKey: "Geçersiz Tenor sorgusu."
            ])
        }

        var request = URLRequest(url: url, timeoutInterval: 15)
        request.setValue(Self.mobileUserAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7", forHTTPHeaderField: "Accept-Language")

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? 0
            throw NSError(domain: "TenorService", code: code, userInfo: [
                NSLocalizedDescriptionKey: "Tenor GIF'leri yüklenemedi (HTTP \(code))."
            ])
        }

        let html = String(data: data, encoding: .utf8) ?? ""
        return parseGifs(from: html, limit: 24)
    }

    func buildTenorUrl(query: String) -> String {
        let clean = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if clean.isEmpty { return "https://tenor.com/" }
        let folded = foldTurkishChars(clean.lowercased())
            .replacingOccurrences(of: "[^a-z0-9\\s-]+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "\\s+", with: "-", options: .regularExpression)

        let slug = folded.isEmpty
            ? (clean.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? clean)
            : folded
        return "https://tenor.com/search/\(slug)-gifs"
    }

    func parseGifs(from html: String, limit: Int = 24) -> [TenorGifItem] {
        guard !html.isEmpty else { return [] }
        let pattern = #"https://media[0-9]*\.tenor\.com/(?:m/)?([A-Za-z0-9_-]+)/([A-Za-z0-9_.-]+)\.gif"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return [] }

        let nsRange = NSRange(html.startIndex..<html.endIndex, in: html)
        let matches = regex.matches(in: html, range: nsRange)

        var orderedIds: [String] = []
        var variantsByAsset: [String: [(url: String, slug: String)]] = [:]

        for match in matches {
            guard let fullRange = Range(match.range(at: 0), in: html),
                  let idRange = Range(match.range(at: 1), in: html),
                  let slugRange = Range(match.range(at: 2), in: html) else { continue }

            let fullUrl = String(html[fullRange])
            let rawId = String(html[idRange])
            let slug = String(html[slugRange])

            let canonicalId: String
            if rawId.count > 6, rawId.suffix(6).hasPrefix("AAAA") {
                canonicalId = String(rawId.dropLast(6))
            } else {
                canonicalId = rawId
            }

            if variantsByAsset[canonicalId] == nil {
                orderedIds.append(canonicalId)
                variantsByAsset[canonicalId] = []
            }
            if !(variantsByAsset[canonicalId]?.contains(where: { $0.url == fullUrl }) ?? false) {
                variantsByAsset[canonicalId]?.append((fullUrl, slug))
            }
        }

        return orderedIds.prefix(limit).compactMap { assetId in
            guard let variants = variantsByAsset[assetId], let first = variants.first else { return nil }
            let urls = variants.map(\.url)
            let preview = urls.first(where: { $0.contains("AAAAM/") })
                ?? urls.first(where: { $0.contains("AAAAd/") })
                ?? urls[0]
            let full = urls.first(where: { $0.contains("AAAAC/") })
                ?? urls.first(where: { $0.contains("AAAAd/") })
                ?? preview
            let title = first.slug
                .replacingOccurrences(of: "-", with: " ")
                .replacingOccurrences(of: "_", with: " ")
                .trimmingCharacters(in: .whitespacesAndNewlines)

            return TenorGifItem(
                id: assetId,
                title: title.isEmpty ? "Tenor GIF" : title,
                fullUrl: full,
                previewUrl: preview
            )
        }
    }

    private func foldTurkishChars(_ input: String) -> String {
        input
            .replacingOccurrences(of: "ç", with: "c")
            .replacingOccurrences(of: "ğ", with: "g")
            .replacingOccurrences(of: "ı", with: "i")
            .replacingOccurrences(of: "ö", with: "o")
            .replacingOccurrences(of: "ş", with: "s")
            .replacingOccurrences(of: "ü", with: "u")
    }
}
