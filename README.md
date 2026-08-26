# AI Keyboard 2

Android için Türkçe odaklı, yapay zeka destekli özel klavye uygulaması. `InputMethodService` üzerine sıfırdan kurulmuş; canlı otomatik düzeltme, küfür/yasaklı kelime filtresi, Fn tuşu kısayolları, sürükleyerek boyutlandırma ve NVIDIA NIM (Llama 3.3 70B) ile klavye içinden metin üretme/düzenleme özellikleri sunar.

## İçindekiler

- [Öne Çıkan Özellikler](#öne-çıkan-özellikler)
- [Mimari ve Proje Yapısı](#mimari-ve-proje-yapısı)
- [Klavye Servisi Detayları](#klavye-servisi-detayları)
- [Otomatik Düzeltme Motoru](#otomatik-düzeltme-motoru)
- [Küfür / Yasaklı Kelime Filtresi](#küfür--yasaklı-kelime-filtresi)
- [Fn Kısayolları](#fn-kısayolları)
- [Yapay Zeka Entegrasyonu](#yapay-zeka-entegrasyonu)
- [Ana Ekran (MainActivity)](#ana-ekran-mainactivity)
- [Gereksinimler](#gereksinimler)
- [Kurulum](#kurulum)
- [Klavyeyi Etkinleştirme](#klavyeyi-etkinleştirme)
- [Derleme](#derleme)
- [İzinler](#izinler)
- [Performans Notları](#performans-notları)

## Öne Çıkan Özellikler

### Klavye Düzeni ve Etkileşim
- Tam Türkçe klavye dizilimi (`ı`, `İ`, `ğ`, `ü`, `ş`, `ö`, `ç` dahil) ve ayrı bir sembol/rakam klavyesi görünümü.
- Dokunuşta anında tepki için `ACTION_DOWN` bazlı özel dokunma işleyicisi (`bindInstantTouch`) — standart `OnClickListener`'ın `ACTION_UP` gecikmesini ortadan kaldırır.
- Tuşa basıldığında yukarıda beliren harf önizleme baloncuğu (popup window ile, ayarlardan kapatılabilir).
- Tuş sesi (`AudioManager.FX_KEYPRESS_STANDARD`) ve dokunsal geri bildirim (12ms tek atımlık titreşim, doğrudan `Vibrator` servisi üzerinden — sistemin "dokunma geri bildirimi" ayarından bağımsız çalışır); her ikisi de ayarlardan açılıp kapatılabilir.
- Üç kademeli Shift mantığı: küçük harf → tek harf büyük → çift tıklama veya uzun basışla Caps Lock.
- Fn tuşuna basılı tutmadan, tek dokunuşla açılıp kapanan Fn modu; aktifken bir sonraki harf tuşu kısayol olarak yorumlanır.
- Çift boşlukla nokta: 350ms içinde art arda iki boşluğa basılırsa aradaki boşluk otomatik olarak ". " ile değiştirilir.
- Cümle sonu noktalamasından (`.`, `!`, `?`) sonra gelen boşlukta bir sonraki harf otomatik büyük harfle başlar; boş bir metin alanına girildiğinde de ilk harf otomatik büyütülür (şifre alanlarında bu devre dışıdır).
- Basılı tutup dikey sürükleyerek klavye yüksekliğini canlı değiştirme (36–64dp aralığında, GBoard'daki basılı-tut-sürükle davranışına benzer); seçilen yükseklik `SharedPreferences`'a kaydedilip bir sonraki açılışta korunur, sürükleme sırasında yüzde göstergesi belirir.
- Geri Al (`android.R.id.undo`) tuşu ve `IME_ACTION_DONE` destekli Enter tuşu.
- Uzun basışta sürekli silme (300ms bekleme + 50ms aralıklarla tekrar).
- Navigasyon çubuğu yüksekliğine göre klavyenin alt kısmına otomatik dolgu (edge-to-edge cihazlarda tuşların sistem çubuğunun altında kalmaması için).

### Canlı Öneri Şeridi
- Yazılmakta olan kelimeye göre 3 slotlu canlı öneri şeridi; kelime tamamlanınca (boşluk/noktalama ile) ortadaki öneri otomatik düzeltme olarak uygulanabilir (ayardan açılıp kapatılabilir).
- Öneri hesaplaması ayrı bir `ExecutorService`'te çalışır; sonuç, hesaplama tamamlandığında hâlâ geçerliyse (kullanıcı o kelimeyi bırakmadıysa) ana thread'e taşınır — eski/yarım kalmış hesaplamalar yok sayılır (`suggestionRequestId` / `suggestionWordContext` ile).

### Yasaklı Kelime Bildirimi
- Yazılan kelime yasaklı listedeyse, boşluk/noktalamaya basıldığı anda kelime tamamen silinir (sansürleme değil) ve klavye araç çubuğunda 2 saniyelik "🚫 … silindi" bandı gösterilir.

## Mimari ve Proje Yapısı

```
app/src/main/java/com/fraunhofer/aikeyboard2/
├── MainActivity.kt                    # Ana ekran — sekmeli arayüz (kurulum, ayarlar, AI, filtre, kısayollar)
├── service/
│   └── CustomKeyboardService.kt       # IME servisi: klavye UI, girdi mantığı, boyutlandırma, AI paneli
├── ai/
│   ├── AiClient.kt                    # NVIDIA NIM chat-completions HTTP istemcisi (HttpURLConnection + org.json)
│   └── ApiKeyProvider.kt              # API key kaynağı: kullanıcı key'i → yoksa test key'ine düşer
├── autocomplete/
│   └── AutoCorrectEngine.kt           # Trie + Levenshtein tabanlı öneri/otomatik düzeltme motoru
├── filter/
│   └── ProfanityFilter.kt             # Normalize edilmiş küfür/yasaklı kelime denetimi
├── data/
│   ├── WordRepository.kt              # Yasaklı kelime deposu (SharedPreferences, Set<String>)
│   └── ShortcutRepository.kt          # Fn + harf kısayolları deposu (SharedPreferences, JSON)
└── ui/
    ├── FilterWordsAdapter.kt          # Yasaklı kelime listesi RecyclerView adapter'ı
    └── ShortcutAdapter.kt             # Kısayol listesi RecyclerView adapter'ı
```

Uygulama iki ana bileşenden oluşur:

1. **`CustomKeyboardService`** — `InputMethodService`'i genişleten, sistemde klavye olarak kayıtlı asıl IME servisi. Tüm klavye çizimi, dokunma işleme, öneri/otomatik düzeltme, filtreleme ve AI paneli mantığı burada yaşar.
2. **`MainActivity`** — kullanıcının klavyeyi etkinleştirmesi, genel ayarları yapılandırması, yasaklı kelimeleri ve Fn kısayollarını yönetmesi, AI API key'ini girmesi için Jetpack Compose/View tabanlı karma bir ayarlar ekranı.

## Klavye Servisi Detayları

`CustomKeyboardService`, `onCreateInputView()` içinde harf klavyesi düzenini (`keyboard_view.xml`), sembol tuşuna basıldığında ise sembol/rakam düzenini (`keyboard_symbols_view.xml`) `setInputView` ile değiştirerek gösterir. Servis şu bileşenleri önbelleğe alıp yönetir:

- **Harf/sembol/rakam haritaları** (`letterMap`, `symKeyMap`, `numKeyMap`) — view ID'lerini karakterlere eşler, `lazy` ile bir kez oluşturulur.
- **Boyutlandırılabilir tuş listesi** (`resizableButtons`) — sürükleyerek boyutlandırma sırasında her karede `findViewById` çağrılmaması için bir kez toplanır.
- **AI istem tamponu** (`aiPromptBuffer`) — AI modundayken harf tuşları gerçek metin alanına değil bu StringBuilder'a yazar, çünkü AI paneli IME'nin kendi penceresi içindedir ve ikinci bir sistem klavyesi açılamaz.
- **Yaşam döngüsü temizliği** — `onFinishInput()`'ta bekleyen handler'lar iptal edilir ve devam eden bir AI isteği varsa `aiRequestId` artırılarak sonucu geçersiz kılınır; `onDestroy()`'da executor'lar kapatılır.

## Otomatik Düzeltme Motoru

`AutoCorrectEngine`, sözlük dosyasını ve kullanıcının özel sözlüğünü tek seferde bir **Trie (ön ek ağacı)** yapısına yükler:

- Trie sayesinde kelime tamamlama sorguları 1 milisaniyenin altında sonuçlanır.
- Yazım hatası (typo) düzeltmesi için **Levenshtein mesafesi** hesaplanır; 76.000+ kelimelik sözlükte arama alanını daraltmak amacıyla kelimeler ilk harflerine göre önceden gruplanır (`wordsByFirstChar`), böylece bir kelime için yalnızca aynı ilk harfe sahip aday grubu taranır.
- Hesaplama her zaman `suggestionExecutor` adlı ayrı bir thread'de yapılır; sözlük taraması ana thread'de çalıştırılırsa klavye donar — bu yüzden sonuç ana thread'e yalnızca hâlâ güncelse aktarılır.

## Küfür / Yasaklı Kelime Filtresi

`ProfanityFilter`, yasaklı kelime setini `WordRepository`'den yükleyip normalize eder:

- Türkçe karaktersiz eşdeğerlere dönüştürme (`ı/İ→i`, `ğ/Ğ→g`, `ü/Ü→u`, `ş/Ş→s`, `ö/Ö→o`, `ç/Ç→c`) allocation'ı en aza indiren bir `StringBuilder` yaklaşımıyla yapılır.
- Kelime seti hash'i değişmediği sürece yeniden yükleme yapılmaz (`loadedHash` dirty-flag deseni).
- Denetim hem tam eşleşmeyi hem de alt dize içeriğini kontrol eder (`normalized == profane || normalized.contains(profane)`).
- Varsayılan yasaklı kelime listesi ilk açılışta otomatik oluşturulur; `MainActivity`'deki **Filtre** sekmesinden kelime ekleyip çıkarılabilir.

## Fn Kısayolları

`ShortcutRepository`, `Fn + harf` kombinasyonlarını `SharedPreferences` içinde JSON olarak saklar. Desteklenen aksiyon tipleri:

| Aksiyon | Açıklama |
|---|---|
| `SELECT_ALL` | Aktif metin alanındaki tüm metni seçer |
| `COPY` | Seçili metni panoya kopyalar |
| `PASTE` | Pano içeriğini yapıştırır |
| `TYPE_TEXT` | Önceden tanımlanmış sabit bir metni yazar |

Kısayollar `MainActivity`'deki **Kısayollar** sekmesinden eklenir/düzenlenir/silinir; klavyede Fn tuşuna basılıp ardından ilgili harfe dokunulduğunda tetiklenir.

## Yapay Zeka Entegrasyonu

Klavyedeki AI tuşuna basıldığında, IME'nin kendi penceresi içinde açılan bir istem paneli devreye girer:

- **Metin seçiliyse:** girilen talimata göre yalnızca seçili metni düzenler (ör. "daha resmi yaz", "kısalt", "İngilizceye çevir").
- **Seçim yoksa:** girilen talimatı doğrudan bir üretim isteği olarak gönderir.

`AiClient`, `https://integrate.api.nvidia.com/v1/chat/completions` uç noktasına `meta/llama-3.3-70b-instruct` modeliyle senkron bir istek atar (harici bir HTTP kütüphanesi kullanmadan, yalnızca `HttpURLConnection` + `org.json` ile). İstek arka planda `aiExecutor` üzerinde çalışır, sonuç ana thread'e taşınır ve panel kapatılmışsa (`aiRequestId` değişmişse) sonuç yok sayılır.

- Seçili metin düzenlemesinde `max_tokens`, girdi uzunluğuna göre dinamik hesaplanır (200–800 arası); serbest üretimde sabit 400 token limiti kullanılır.
- Yanıt çevresindeki olası tırnak işaretleri otomatik temizlenir.
- HTTP durum kodlarına göre kullanıcı dostu hata mesajları üretilir (401/403 → geçersiz key, 429 → limit aşımı, 5xx → sunucu hatası).

**API key yönetimi** (`ApiKeyProvider`) "bring your own key" prensibiyle çalışır:
1. Kullanıcının ayarlar ekranından girdiği kendi key'i öncelikli olarak kullanılır.
2. Kullanıcı key girmemişse, yalnızca geliştirme/test amacıyla `local.properties` dosyasındaki `NVIDIA_NIM_API_KEY` değerine (build-time `BuildConfig` alanı olarak gömülür) düşülür.
3. Hiçbir key yoksa istek gönderilmez ve kullanıcıya "API key yok" uyarısı gösterilir.

## Ana Ekran (MainActivity)

Beş sekmeli bir arayüz sunar:

1. **Kurulum** — klavyeyi sistemde etkinleştirme adımları ve durum rozeti (✅ Etkin / ⚠️ Pasif).
2. **Ayarlar** — tuş sesi, titreşim, tuş önizleme baloncuğu, çift boşlukla nokta, otomatik büyük harf ve otomatik düzeltme gibi tercihler.
3. **AI** — NVIDIA NIM API key girişi/temizleme ve hangi key'in (kullanıcı/test) kullanıldığının gösterimi.
4. **Filtre** — yasaklı kelime listesini görüntüleme, kelime ekleme/silme.
5. **Kısayollar** — Fn + harf kombinasyonlarını listeleme, ekleme, silme.

## Gereksinimler

- Android Studio (güncel sürüm)
- JDK 11
- Android SDK — `minSdk 24`, `targetSdk`/`compileSdk 35`
- Kotlin, AndroidX (AppCompat, RecyclerView) ve Jetpack Compose (Material 3, BOM ile yönetilir)

## Kurulum

1. Depoyu klonlayın ve Android Studio ile açın.
2. (Opsiyonel) Geliştirme sırasında AI özelliğini test etmek için proje kök dizinindeki `local.properties` dosyasına kendi NVIDIA NIM key'inizi ekleyin:
   ```properties
   NVIDIA_NIM_API_KEY=your_api_key_here
   ```
   Bu dosya `.gitignore` içindedir ve repoya gönderilmez; `app/build.gradle.kts` bu değeri `BuildConfig.NVIDIA_NIM_TEST_API_KEY` alanına gömer.
3. Gradle senkronizasyonunu bekleyin, ardından `app` modülünü bir cihaz/emülatörde çalıştırın.

## Klavyeyi Etkinleştirme

1. Uygulamayı açın, **Kurulum** sekmesindeki adımları izleyerek Android **Ayarlar > Sistem > Diller ve Giriş > Sanal Klavye**'den "AI Keyboard 2"yi etkinleştirin.
2. Herhangi bir metin alanına dokunup klavye seçiciden (klavye simgesi) bu klavyeyi seçin.
3. **Ayarlar** sekmesinden tuş yüksekliği/davranış tercihlerini, **AI** sekmesinden kendi API key'inizi, **Filtre** ve **Kısayollar** sekmelerinden ilgili listeleri yapılandırabilirsiniz.

## Derleme

```bash
./gradlew assembleDebug
```

Windows için:

```powershell
.\gradlew.bat assembleDebug
```

Release derlemesi `isMinifyEnabled` ve `isShrinkResources` ile ProGuard/R8 küçültmesi kullanır (`app/proguard-rules.pro`).

## İzinler

| İzin | Amaç |
|---|---|
| `INTERNET` | AI panelinden NVIDIA NIM API'sine istek göndermek için |
| `VIBRATE` | Tuş basımında dokunsal geri bildirim için |

## Performans Notları

- Sözlük yükleme ve öneri/typo hesaplamaları ana (UI) thread'ini kilitlememesi için ayrı `ExecutorService`'lerde çalıştırılır; aksi halde 76k+ kelimelik sözlükte yapılacak bir Levenshtein taraması klavyeyi donduracak kadar uzun sürebilir.
- Boyutlandırma sırasında tuş view referansları önbelleğe alınır, sürükleme her karede `findViewById` çağırmaz.
- Yasaklı kelime seti ve öneri sonuçları, içerik değişmediği sürece yeniden hesaplanmaz (hash/dirty-flag kontrolleri).
- Kod içi yorumlar Türkçedir; proje Türkçe kullanıcı kitlesi gözetilerek geliştirilmiştir.
