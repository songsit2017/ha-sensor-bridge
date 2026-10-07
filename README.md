# HA Sensor Bridge (Android)

แอป Android ตัวเล็ก (Kotlin + Jetpack Compose) ที่ส่งค่าเซนเซอร์ความเร่ง (accelerometer) ของมือถือเข้า Home Assistant
ใช้คู่กับ HA Companion app ได้เลย ไม่ได้มาแทน Companion

## ทำงานยังไง

1. ใส่ URL ของ HA แล้วกด "เข้าสู่ระบบด้วยบัญชี HA" แอปเปิดหน้าล็อกอินของ HA ในเบราว์เซอร์ (OAuth2 แบบเดียวกับ Companion:
   `/auth/authorize` แล้วแลก code ที่ `/auth/token`) แต่ละคนล็อกอินด้วย user ของตัวเอง ไม่ต้องสร้าง token
   access token หมดอายุเร็ว แอปใช้ refresh token ต่ออายุเอง ทั้งหมดเก็บเข้ารหัสด้วยกุญแจใน Android Keystore
   (ตัวเลือกขั้นสูง: ใช้ Long-lived access token แทนได้)
   - `client_id` ของ OAuth คือ `auth-client.html` ใน repo นี้ ซึ่งประกาศ redirect `hasensorbridge://auth-callback`
     HA ต้องเข้าถึงไฟล์นี้ได้ทางอินเทอร์เน็ต จึงต้องเป็น repo public
2. แอปลงทะเบียนมือถือกับ integration `mobile_app` (`POST /api/mobile_app/registrations`) ได้ `webhook_id`
   มือถือจะขึ้นเป็นอุปกรณ์แยกต่างหากจาก Companion
3. เปิดแอปแล้วเริ่มส่งทันที (กด "หยุดส่ง" แล้วจะไม่เริ่มเองจนกว่าจะกด "เริ่มส่ง") Foreground service อ่าน accelerometer ด้วย `SensorManager` แล้วส่งเข้า HA ทาง
   `POST /api/webhook/<webhook_id>` (`register_sensor` ครั้งแรก, `update_sensor_states` ทุกรอบ) ส่งต่อได้แม้ปิดจอ

## ชื่อเครื่อง

แอปใช้ชื่อที่เจ้าของตั้งไว้ในมือถือ (Settings → About phone → Device name, ถ้าไม่มีใช้ชื่อ Bluetooth, ถ้าไม่มีอีกใช้ชื่อรุ่น)
แก้ชื่อได้ตอนล็อกอินและในแอปภายหลัง ชื่อที่เปลี่ยนจะส่งเข้า HA ผ่าน `update_registration` ไม่ต้องลงทะเบียนใหม่

## กันระบบปิดแอป

- การ์ด "กันระบบปิดแอปตอนประหยัดแบต" ในแอปแสดงสถานะการยกเว้นและมีปุ่มขอยกเว้น (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
- Service ใช้ `START_STICKY` และรีสตาร์ทเองหลังเครื่องรีบูตหรือแอปถูกอัปเดต ถ้าผู้ใช้ไม่ได้กดหยุดส่งไว้
- Xiaomi, Samsung, Oppo/Realme/OnePlus, Vivo, Huawei มีปุ่มเปิดหน้าตั้งค่าเริ่มอัตโนมัติ/แบตของยี่ห้อนั้น พร้อมคำแนะนำ
  (หน้าตั้งค่าต่างกันตามเวอร์ชัน OS ถ้าเปิดไม่ได้จะไปหน้าข้อมูลแอปแทน) ดูเพิ่มที่ dontkillmyapp.com

## Entity ที่จะโผล่ใน HA

- `sensor.<ชื่อเครื่อง>_accelerometer_x` / `_y` / `_z` (m/s²)
- `sensor.<ชื่อเครื่อง>_acceleration` ขนาดรวม (m/s², วางนิ่งอยู่ ~9.8 เพราะรวมแรงโน้มถ่วง)
- `sensor.<ชื่อเครื่อง>_tilt_pitch` / `_tilt_roll` มุมเอียง (องศา) pitch 0 = วางราบ, ±90 = ตั้งตรง
  roll 0 = วางราบ, ±90 = ตะแคงข้าง
- `binary_sensor.<ชื่อเครื่อง>_motion` เป็น `on` ถ้าความเร่งสุทธิ (หักแรงโน้มถ่วงออกด้วย low-pass filter)
  เกินเกณฑ์ในรอบนั้น มี attribute `peak_linear_acceleration`
- `binary_sensor.<ชื่อเครื่อง>_shake` เป็น `on` 3 วินาทีหลังเขย่า (กระแทกแรง 3 ครั้งใน 1 วินาที) ส่งเข้า HA ทันทีไม่รอรอบ
- `binary_sensor.<ชื่อเครื่อง>_face_down` เป็น `on` ตอนคว่ำหน้าจอลง ส่งเข้า HA ทันทีเมื่อเปลี่ยนสถานะ

ตั้งความถี่การส่ง (0.5–30 วินาที) และเกณฑ์การเคลื่อนที่ได้ในแอป

## อัปเดตอัตโนมัติ

- ทุกครั้งที่ push เข้า `main` GitHub Actions จะ build APK เซ็นด้วยกุญแจคงที่ แล้วปล่อยเป็น GitHub Release (tag `v<เลข build>`)
- แอปเช็ก Release ล่าสุดทุกครั้งที่เปิด (หรือกด "ตรวจสอบอัปเดต") ถ้ามีเวอร์ชันใหม่จะโหลด ตรวจ checksum แล้วเปิดตัวติดตั้งของระบบ
- Android ยอมอัปเดตทับก็ต่อเมื่อ APK เซ็นด้วยกุญแจเดียวกับที่ติดตั้งอยู่ จึงปลอดภัยแม้ไฟล์ถูกแก้
- repo ต้องเป็น **public** เพื่อให้แอปอ่าน Release ได้โดยไม่ต้องฝัง token ไว้ในแอป
- ตั้งค่าครั้งเดียว: เพิ่ม secrets `KEYSTORE_BASE64` และ `KEYSTORE_PASSWORD` ใน Settings → Secrets and variables → Actions
  ถ้ายังไม่มี secrets workflow จะ build ให้แต่ไม่ปล่อย Release
- ติดตั้ง APK ที่เซ็นด้วยกุญแจนี้ครั้งแรกต้องถอนเวอร์ชัน debug เดิมออกก่อน หลังจากนั้นอัปเดตทับได้เสมอ

## Build

ต้องใช้ JDK 17 และ Android SDK (เปิดด้วย Android Studio ได้เลย)

```bash
./gradlew assembleDebug
# APK อยู่ที่ app/build/outputs/apk/debug/app-debug.apk
```

ถ้า build เองจะเซ็นด้วยกุญแจ debug ของเครื่อง

## หมายเหตุ

- เปิด `usesCleartextTraffic` ไว้ เพื่อให้ใช้ `http://homeassistant.local:8123` ในบ้านได้
- ยังไม่เคย compile หรือลองกับ HA จริง
- เวอร์ชันเต็มที่มีหน้าควบคุมอุปกรณ์ (WebSocket) และแท็บกล้อง go2rtc/RTSP เก็บไว้ที่ `haos-native-full.zip` ต่างหาก
