package dev.reelscounter.app.service

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils

/** Accessibility-service status checks and settings deep links. */
object ServiceStatus {
  private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

  /** Enabled by the user in Settings (may not be bound yet). */
  fun isEnabled(context: Context): Boolean {
    val expected = ComponentName(context, ReelsAccessibilityService::class.java)
    val enabled = Settings.Secure.getString(
      context.contentResolver,
      Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabled)
    for (entry in splitter) {
      if (ComponentName.unflattenFromString(entry) == expected) return true
    }
    return false
  }

  /** Enabled and currently bound by the system. */
  val isRunning: Boolean
    get() = ReelsAccessibilityService.isRunning

  /** Deep-links to this service's page where supported, else the accessibility list. */
  fun openAccessibilitySettings(context: Context) {
    val component = ComponentName(context, ReelsAccessibilityService::class.java).flattenToString()
    val details = Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS)
      .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
      context.startActivity(details)
    } catch (_: ActivityNotFoundException) {
      openGenericAccessibility(context)
    } catch (_: SecurityException) {
      openGenericAccessibility(context)
    }
  }

  /** App info screen: on Android 13+ sideloaded apps need "Allow restricted settings" from its ⋮ menu. */
  fun openAppDetails(context: Context) {
    context.startActivity(
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
  }

  private fun openGenericAccessibility(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }
}
