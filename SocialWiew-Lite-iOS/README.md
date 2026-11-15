# SocialWiew Lite iOS (iOS 12.0+ UIKit Optimize Sürüm)

Bu klasör, eski nesil cihazlar (**iPhone 5s, iPhone 6, iPad Air — iOS 12.0 ve üzeri**) için özel olarak hafifletilmiş ve optimize edilmiş **SocialWiew Lite** sürümünü içerir. Modern SwiftUI + Liquid Glass sürümü `/ios/` klasöründe ayrı olarak korunmaktadır.

## iOS 12 ve Düşük RAM Optimizasyonları
- **Saf UIKit Mimarisi:** SwiftUI, Combine (`@Published`), `UIWindowScene` veya SF Symbols gibi iOS 13+ API'leri kullanılmaz; `AppDelegate + UIWindow` ve CoreGraphics vektör ikonları ile iOS 12.0+ üzerinde doğrudan çalışır.
- **Tek Aktif `AVPlayer` Havuzu:** Ekranda aynı anda yalnızca tek video oynatılır; kaydırılan hücrelerde `AVPlayerItem` anında bellekten boşaltılır.
- **Sınırlı Bellek & Disk Önbelleği:** Görsel önbelleği (`NSCache`) 12 MB ile, yerel bulut video önbelleği ise otomatik temizlemeli **60 MB** sınırıyla çalışır.
- **Bloklama/Blur Yok:** A7/A8 GPU'larda kare düşüşünü önlemek için canlı bulanıklaştırma efektleri yerine yüksek kontrastlı koyu yüzeyler kullanılır.
- **Firebase 10.29.0 + GoogleSignIn 7.1.0:** iOS 12 uyumlu son SDK sürümleriyle aynı Firestore veritabanına (`ai-studio-socialwiew-76843402-c996-4e5f-82f3-b5c50f47323a`) bağlanır.

## Xcode / GitHub Actions ile `.ipa` Oluşturma
- **GitHub Actions (Mac gerekmez):** `.github/workflows/build-ios-lite-ipa.yml` ile `SocialWiew-Lite-iOS12-unsigned.ipa` çıktısı alabilirsiniz.
- **macOS Terminal:**
  ```bash
  cd SocialWiew-Lite-iOS
  brew install xcodegen
  xcodegen generate
  open SocialWiewLite.xcodeproj
  ```
- Gerçek cihazda Firebase için Firebase Console'dan `com.aistudio.socialwiew.lite` paket adına ait `GoogleService-Info.plist` dosyasını `SocialWiewLite/Resources/` altına ekleyin.
