# SilentPipe - Trình phát media ẩn danh & mạnh mẽ

**SilentPipe** là một ứng dụng Android mã nguồn mở được thiết kế để phát nhạc và video từ các nền tảng phổ biến (YouTube, TikTok, v.v.) mà không cần quảng cáo, không theo dõi người dùng, và hỗ trợ chạy nền hoàn hảo.

Dự án được xây dựng với tinh thần "tự do software" (Free Software), sử dụng các công nghệ mạnh mẽ để bypass các hạn chế thông thường.

## 🚀 Tính năng chính

-   **Share to Play**: Không cần mở app rườm rà. Chỉ cần nhấn "Chia sẻ" (Share) từ YouTube/TikTok và chọn **SilentPipe**.
-   **Spotify Support**: Hỗ trợ link Spotify (Track), tự động tìm bài tương ứng trên YouTube để phát. Hỗ trợ cả Playlist (YouTube) - tự động phát bài đầu tiên.
-   **Download Offline**: Tải nhạc/video về bộ nhớ máy (thư mục Music/SilentPipe) để nghe Offline.
-   **Playback Speed**: Điều chỉnh tốc độ phát linh hoạt (0.5x, 1.25x, 2.0x...) hoặc tự nhập con số bất kỳ bạn thích (ví dụ 1.05).
-   **Player Controls**: Nút **Thu nhỏ (Minimize)** giúp ẩn video để thao tác với menu/danh sách mà không bị che khuất.
-   **Now Playing Metadata**: Hiển thị rõ ràng tên bài hát và nghệ sĩ trên trình phát.
-   **Favorites & History**: Lưu lại các bài hát yêu thích và quản lý danh sách phát cá nhân. Chặn thêm trùng lặp.
-   **Quick Settings Tile**: Thêm nút vào thanh trạng thái để phát ngay link đang copy trong Clipboard. Hỗ trợ tự thêm từ trong Cài đặt (Android 13+).
-   **Home Screen Shortcut**: Tạo shortcut ngoài màn hình chính, ấn là phát ngay nhạc từ Clipboard.
-   **Backup & Restore**: Sao lưu dữ liệu yêu thích ra file JSON để chuyển sang máy khác.
-   **Background Playback**: Hỗ trợ phát nhạc nền khi tắt màn hình, sử dụng **Android Media3 Service** chuẩn chỉ.
-   **Python Powered Integration**: Tích hợp **Chaquopy** để chạy trực tiếp **yt-dlp** (thư viện tải video mạnh nhất thế giới) ngay trên điện thoại, giúp bóc tách link media cực mạnh.
-   **Privacy First**: Không đăng nhập, không lưu lịch sử (trừ khi bạn muốn), không gửi dữ liệu về máy chủ lạ.
-   **No Ads**: Hoàn toàn sạch bóng quảng cáo.

## 🛠️ Công nghệ sử dụng

-   **Ngôn ngữ**: Java / Kotlin
-   **Build System**: Gradle Kotlin DSL (`.gradle.kts`)
-   **Core Libraries**:
    -   `androidx.media3`: Trình phát media thế hệ mới của Google.
    -   `NewPipeExtractor`: Thư viện bóc tách dữ liệu siêu nhẹ từ dự án NewPipe.
    -   `Chaquopy`: Plugin chạy Python trên Android.
    -   `OkHttp`: Xử lý network request tối ưu.

## 📦 Cài đặt & Build

Dự án yêu cầu Android Studio và JDK 17 trở lên.

1.  **Clone dự án**:
    ```bash
    git clone https://github.com/thtcsec/SilentPipe.git
    cd SilentPipe
    ```

2.  **Cấu hình**:
    Mở file `local.properties` (nếu chưa có thì tạo mới) và trỏ đường dẫn SDK:
    ```properties
    sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
    ```

3.  **Build**:
    Chạy lệnh sau để build file APK debug:
    ```bash
    ./gradlew clean assembleDebug
    ```

4.  **Cài đặt**:
    File APK sẽ nằm ở `app/build/outputs/apk/debug/app-debug.apk`. Copy vào điện thoại và cài đặt.

## 🤝 Đóng góp (Contribute)

Mọi đóng góp đều được hoan nghênh! Nếu bạn tìm thấy lỗi hoặc muốn thêm tính năng mới:

1.  Fork dự án này.
2.  Tạo branch mới (`git checkout -b feature/TinhNangMoi`).
3.  Commit thay đổi (`git commit -m 'Thêm tính năng X'`).
4.  Push lên branch (`git push origin feature/TinhNangMoi`).
5.  Tạo Pull Request.

## 📜 Giấy phép (License)

Dự án này được phát hành dưới giấy phép **GNU General Public License v3.0 (GPLv3)**.
Xem file [LICENSE](LICENSE) để biết thêm chi tiết.

Điều này có nghĩa là bạn được tự do sử dụng, sửa đổi và phân phối lại, NHƯNG các bản sửa đổi cũng phải là mã nguồn mở (Open Source) theo GPLv3.

---
**Author**: tuhoang / thtcsec
