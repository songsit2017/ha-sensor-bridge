package dev.haos.nativeapp.sensor

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** Helpers to keep Android's battery savers from killing the reporting service. */
object BatterySettings {

    fun isExempt(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** Shows the system dialog asking to let the app ignore battery optimization. */
    fun requestExemption(context: Context) {
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        launch(context, request) || launch(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    /** Vendor skins add their own auto-start / background limits on top of stock Android. */
    enum class Vendor(val label: String, val steps: String) {
        XIAOMI(
            "Xiaomi / Redmi / POCO",
            "เปิด 'เริ่มอัตโนมัติ' (Autostart) ให้แอปนี้ แล้วที่หน้าข้อมูลแอปตั้ง 'ประหยัดแบตเตอรี่' เป็น 'ไม่มีข้อจำกัด' " +
                "และล็อกแอปไว้ในหน้า Recent (ดึงการ์ดลงแล้วกดรูปกุญแจ)",
        ),
        SAMSUNG(
            "Samsung",
            "ที่ แบตเตอรี่ → ขีดจำกัดการใช้งานเบื้องหลัง ให้เอาแอปนี้ออกจาก 'แอปที่เข้าสู่โหมดพักเครื่อง' " +
                "และ 'แอปที่เข้าสู่โหมดพักเครื่องลึก' แล้วเพิ่มเข้า 'แอปที่ไม่เข้าสู่โหมดพักเครื่อง'",
        ),
        OPPO(
            "Oppo / Realme / OnePlus",
            "เปิด 'อนุญาตให้เริ่มอัตโนมัติ' และ 'อนุญาตการทำงานเบื้องหลัง' ให้แอปนี้ ที่หน้าข้อมูลแอป → การใช้แบตเตอรี่ " +
                "เลือก 'ไม่จำกัด' และล็อกแอปไว้ในหน้า Recent",
        ),
        VIVO(
            "Vivo / iQOO",
            "เปิด 'เริ่มอัตโนมัติ' และ 'ทำงานเบื้องหลังที่ใช้พลังงานสูง' ให้แอปนี้ในตั้งค่าแบตเตอรี่ แล้วล็อกแอปไว้ในหน้า Recent",
        ),
        HUAWEI(
            "Huawei / Honor",
            "ที่ เปิดแอป → จัดการเอง (Manage manually) ให้เปิดทั้ง 'เริ่มอัตโนมัติ' 'เริ่มจากแอปอื่น' และ 'ทำงานเบื้องหลัง'",
        ),
    }

    fun detectVendor(): Vendor? {
        val maker = android.os.Build.MANUFACTURER.lowercase()
        return when {
            listOf("xiaomi", "redmi", "poco").any { it in maker } -> Vendor.XIAOMI
            "samsung" in maker -> Vendor.SAMSUNG
            listOf("oppo", "realme", "oneplus").any { it in maker } -> Vendor.OPPO
            listOf("vivo", "iqoo").any { it in maker } -> Vendor.VIVO
            listOf("huawei", "honor").any { it in maker } -> Vendor.HUAWEI
            else -> null
        }
    }

    /**
     * Opens the vendor's auto-start / battery page. The screens differ between OS versions, so each
     * known component is tried in turn, ending at the app's own info page, which always exists.
     */
    fun openVendorSettings(context: Context, vendor: Vendor) {
        val candidates = when (vendor) {
            Vendor.XIAOMI -> listOf(
                "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            )
            Vendor.SAMSUNG -> listOf(
                "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
                "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
            )
            Vendor.OPPO -> listOf(
                "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
                "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
                "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
            )
            Vendor.VIVO -> listOf(
                "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            )
            Vendor.HUAWEI -> listOf(
                "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
            )
        }
        val opened = candidates.any { (pkg, cls) ->
            launch(context, Intent().setComponent(ComponentName(pkg, cls)))
        }
        if (!opened) openAppInfo(context)
    }

    fun openAppInfo(context: Context) {
        launch(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
