package dev.haos.nativeapp.sensor

/**
 * A user-switchable set of sensors; turning one off stops its hardware from being read at all.
 * Only the basics are on by default; the extra hardware and detectors start off to save battery.
 */
enum class SensorGroup(val key: String, val label: String, val hint: String, val defaultOn: Boolean = true) {
    ACCEL("accel", "ความเร่ง x/y/z + การเคลื่อนที่", "ค่าความเร่งและตัวตรวจจับการขยับ"),
    TILT("tilt", "การเอียง", "เอียงหน้า-หลัง / ซ้าย-ขวา (องศา)"),
    SHAKE("shake", "เขย่า", "เหตุการณ์เขย่าเครื่อง", defaultOn = false),
    FACE_DOWN("face_down", "คว่ำหน้าจอ", "วางคว่ำหน้าจอลงกับโต๊ะ"),
    GYRO("gyro", "ไจโรสโคป", "ความเร็วการหมุน (°/s)", defaultOn = false),
    COMPASS("compass", "เข็มทิศ", "ทิศเป็นองศา + ชื่อทิศ (N, NE, ...)", defaultOn = false),
    DOUBLE_TAP("double_tap", "แตะสองที", "เคาะตัวเครื่อง/โต๊ะสองครั้งติด", defaultOn = false),
    PICK_UP("pick_up", "หยิบเครื่องขึ้น", "หยิบจากที่วางนิ่งๆ ขึ้นมา", defaultOn = false),
    FALL("fall", "ตกหล่น", "ตรวจจับร่วงอิสระแล้วกระแทก", defaultOn = false),
    MAGNETIC("magnetic", "สนามแม่เหล็ก", "ความแรงสนามแม่เหล็ก (µT) ใช้จับแม่เหล็กประตู/ตู้ หรือโลหะใกล้เครื่อง", defaultOn = false),
    VIBRATION("vibration", "ระดับการสั่นสะเทือน", "วางบนเครื่องซักผ้า/ลำโพงเพื่อรู้ว่ากำลังทำงาน (m/s²)", defaultOn = false),
    POSTURE("posture", "ท่าวางเครื่อง", "หงาย / คว่ำ / ตั้ง / กลับหัว / ตะแคงซ้าย / ตะแคงขวา"),
    SOUND("sound", "ระดับเสียงรอบตัว (dB)", "ใช้ไมค์วัดความดังเท่านั้น ไม่บันทึกและไม่ส่งเสียงออกไป", defaultOn = false),
}
