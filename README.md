# Cruise Activity and Service Management System - POS Client (Android)

> **Jira Story:** [KAN-37](https://huytran20052022.atlassian.net/browse/KAN-37)  
> **Jira Task:** [KAN-35](https://huytran20052022.atlassian.net/browse/KAN-35) - *Initialize Frontend Project — Khởi tạo dự án frontend (POS Client)*

---

## 1. Tổng quan dự án (Project Overview)
Ứng dụng POS di động dành cho nhân viên dịch vụ và bán hàng trên tàu (**Onboard Sales and Service Staff**) chạy trên thiết bị **Smart POS cầm tay** hoặc **Máy tính bảng (Android Tablet)**.

### Chức năng chính:
* **Ghi nhận chi tiêu phi tiền mặt (Cashless Onboard Account):** Quét mã QR, đọc thẻ du thuyền (Cruise Card) hoặc vòng tay RFID/NFC của hành khách để trừ tiền/ghi nợ tạm tính vào tài khoản cabin.
* **Hoạt động ngoại tuyến (Offline Ready):** Lưu trữ tạm thời các giao dịch tại bộ nhớ máy khi tàu đi qua vùng biển mất kết nối mạng và tự động đồng bộ lên Backend API khi có mạng trở lại.
* **Kiểm tra dịch vụ gói tour:** Nhận diện nhanh dịch vụ nào đã bao gồm miễn phí trong gói tour của khách và dịch vụ nào cần tính phí phát sinh.

---

## 2. Công nghệ sử dụng (Tech Stack)
* **Nền tảng:** Android (API Level 24+ / Android 7.0 đến Android 14/15)
* **Ngôn ngữ:** Kotlin
* **UI Toolkit:** Jetpack Compose & Material 3
* **Build System:** Gradle (Kotlin DSL `build.gradle.kts`)
* **Kiến trúc đề xuất:** MVVM + Clean Architecture, Room Database (cho lưu trữ offline), Retrofit/Ktor Client (kết nối Backend Gateway)

---

## 3. Kết nối Backend API
* **Backend Gateway URL:**
  * Android Emulator: `http://10.0.2.2:8080/api`
  * Thiết bị POS thật (cùng mạng Wi-Fi trên tàu): `http://<SERVER_IP>:8080/api`
* **Health Check Endpoint:** `GET /api/health`
* **POS Transactions Endpoint:** `POST /api/pos/transactions` (hỗ trợ header `X-Idempotency-Key` chống trùng lặp)

---

## 4. Hướng dẫn khởi chạy
1. Mở dự án bằng **Android Studio**.
2. Đợi Gradle Sync hoàn tất.
3. Chọn thiết bị ảo (Android Emulator) hoặc kết nối thiết bị POS thật qua USB/Wi-Fi Debugging.
4. Bấm **Run** (`Shift + F10`).
