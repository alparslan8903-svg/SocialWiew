# SocialWiew iOS (Xcode / App Store)

Bu klasör, **SocialWiew** uygulamasının **macOS üzerinde Xcode** ile açılıp **App Store Connect**'e yüklenebilmesi için hazırlanmış yerel **Swift / SwiftUI (iOS 17+)** kaynak kodunu içerir.

> **Not:** Xcode ve iOS SDK yalnızca **macOS** üzerinde çalıştığı için iOS derlemesi ve `.ipa` arşivi Mac bilgisayarınızda yapılmalıdır.

## Xcode Projesi Oluşturma Adımları (macOS)

### Yöntem 1: XcodeGen ile otomatik oluşturma (Önerilen)
1. Bu `ios/` klasörünü Mac bilgisayarınıza kopyalayın.
2. Terminalde `brew install xcodegen` komutuyla XcodeGen'i kurun.
3. `ios/` klasörü içinde çalıştırın:
   ```bash
   xcodegen generate
   open SocialWiew.xcodeproj
   ```

### Yöntem 2: Xcode içinden manuel oluşturma
1. Xcode'da **File > New > Project > iOS > App** (Interface: SwiftUI, Language: Swift, Minimum Deployment: iOS 17.0) oluşturun.
2. `ios/SocialWiew/Sources/` ve `ios/SocialWiew/Resources/FirebaseAppletConfig.plist` dosyalarını projeye sürükleyin ve `Info.plist` içindeki `NSPhotoLibraryUsageDescription` anahtarını ekleyin.
3. **File > Add Package Dependencies...** ile aşağıdaki Swift Package Manager (SPM) paketlerini ekleyin:
   - `https://github.com/firebase/firebase-ios-sdk` (`FirebaseAuth`, `FirebaseFirestore`)
   - `https://github.com/google/GoogleSignIn-iOS` (`GoogleSignIn`, `GoogleSignInSwift`)

## Firebase & Google Giriş Kurulumu
1. Firebase Console'da projenize (`rugged-codex-n9v0l`) bir **iOS Uygulaması** ekleyin (Bundle ID örn. `com.aistudio.socialwiew`).
2. İndirdiğiniz **`GoogleService-Info.plist`** dosyasını `SocialWiew/Resources/` altına ekleyin (Target Membership işaretli olsun).
3. Xcode'da **Target > Info > URL Types** kısmına `GoogleService-Info.plist` içindeki `REVERSED_CLIENT_ID` değerini URL Scheme olarak ekleyin.
4. `FirebaseAppletConfig.plist` içindeki `FirestoreDatabaseId` (`ai-studio-socialwiew-76843402-c996-4e5f-82f3-b5c50f47323a`) otomatik olarak kullanılır.

## App Store Connect'e Yükleme
1. **Signing & Capabilities** sekmesinde kendi **Apple Developer Team** hesabınızı ve benzersiz Bundle Identifier'ınızı seçin.
2. Hedef cihaz olarak **Any iOS Device (arm64)** seçin.
3. **Product > Archive** işlemini çalıştırın ve **Organizer > Distribute App > App Store Connect** adımlarını izleyin.
