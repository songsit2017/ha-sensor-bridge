# HA Sensor Bridge (Android)

แอป Android ตัวเล็ก (Kotlin + Jetpack Compose) ที่ส่งค่าเซนเซอร์ความเร่ง (accelerometer) ของมือถือเข้า Home Assistant
ใช้คู่กับ HA Companion app ได้เลย ไม่ได้มาแทน Companion

## ทำงานยังไง

1. ใส่ URL ของ HA กับ Long-lived access token (สร้างที่ HA → โปรไฟล์ → Security) token เก็บแบบเข้ารหัสด้วยกุญแจใน Android Keystore
2. แอปลงทะเบียนมือถือกับ integration `mobile_app` (`POST /api/mobile_app/registrations`) ได้ `webhook_id`
   มือถือจะขึ้นเป็นอุปกรณ์แยกต่างหากจาก Companion
3. Foreground service อ่าน accelerometer ด้วย `SensorManager` แล้วส่งเข้า HA ทาง
   `POST /api/webhook/<webhook_id>` (`register_sensor` ครั้งแรก, `update_sensor_states` ทุกรอบ) ส่งต่อได้แม้ปิดจอ

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

## Build

ต้องใช้ JDK 17 และ Android SDK (เปิดด้วย Android Studio ได้เลย)

```bash
./gradlew assembleDebug
# APK อยู่ที่ app/build/outputs/apk/debug/app-debug.apk
```

เมื่อ push ขึ้น GitHub, `.github/workflows/build.yml` จะ build APK ให้และแนบเป็น artifact

## หมายเหตุ

- เปิด `usesCleartextTraffic` ไว้ เพื่อให้ใช้ `http://homeassistant.local:8123` ในบ้านได้
- ยังไม่เคย compile หรือลองกับ HA จริง
- เวอร์ชันเต็มที่มีหน้าควบคุมอุปกรณ์ (WebSocket) และแท็บกล้อง go2rtc/RTSP เก็บไว้ที่ `haos-native-full.zip` ต่างหาก
