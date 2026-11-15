import Foundation

final class LiteTenorService {
    private static let mobileUserAgent =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 12_5_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/12.1.2 Mobile/15E148 Safari/604.1"

    func loadGifs(query: String, completion: @escaping (Result<[LiteTenorGifItem], Error>) -> Void) {
        guard let url = URL(string: buildTenorUrl(query: query)) else {
            DispatchQueue.main.async {
                completion(.failure(NSError(domain: "LiteTenorService", code: -1, userInfo: [
                    NSLocalizedDescriptionKey: "Geçersiz Tenor sorgusu."
                ])))
            }
            return
        }

        var request = URLRequest(url: url, timeoutInterval: 15)
        request.setValue(Self.mobileUserAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7", forHTTPHeaderField: "Accept-Language")

        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error {
                DispatchQueue.main.async { completion(.failure(error)) }
                return
            }
            guard let http = response as? HTTPURLResponse,
                  (200..<300).contains(http.statusCode),
                  let data else {
                let code = (response as? HTTPURLResponse)?.statusCode ?? 0
                DispatchQueue.main.async {
                    completion(.failure(NSError(domain: "LiteTenorService", code: code, userInfo: [
                        NSLocalizedDescriptionKey: "Tenor GIF'leri yüklenemedi (HTTP \(code))."
                    ])))
                }
                return
            }

            let html = String(data: data, encoding: .utf8) ?? ""
            let gifs = self.parseGifs(from: html, limit: 16)
            DispatchQueue.main.async { completion(.success(gifs)) }
        }.resume()
    }

    private func buildTenorUrl(query: String) -> String {
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

    private func parseGifs(from html: String, limit: Int) -> [LiteTenorGifItem] {
        guard !html.isEmpty else { return [] }
        let pattern = "https://media[0-9]*\\.tenor\\.com/(?:m/)?([A-Za-z0-9_-]+)/([A-Za-z0-9_.-]+)\\.gif"
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
            let urls = variants.map { $0.url }
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

            return LiteTenorGifItem(
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
