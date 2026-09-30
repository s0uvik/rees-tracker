package dev.reelscounter.app.ui

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.reelscounter.app.ReelsApp
import dev.reelscounter.app.badge.BadgeConfig
import dev.reelscounter.app.badge.BadgeSize
import dev.reelscounter.app.badge.BadgeTapAction
import dev.reelscounter.app.badge.BadgeVisibility
import dev.reelscounter.app.data.DataEvents
import dev.reelscounter.app.service.ReelsAccessibilityService
import dev.reelscounter.app.service.ServiceStatus
import dev.reelscounter.app.stats.Format
import dev.reelscounter.app.stats.HourRow
import dev.reelscounter.app.stats.Period
import dev.reelscounter.app.stats.Stats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

/**
 * App-wide state: service status, onboarding flag, the stats rollup, and all
 * settings. Settings live in SharedPreferences so the service reads the same
 * values; this class listens to them so UI and badge never disagree.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
  private val app = application as ReelsApp
  private val store = app.store
  private val prefs = app.prefs

  data class StatsState(val rows: List<HourRow> = emptyList(), val today: LocalDate = LocalDate.now(), val loaded: Boolean = false)

  data class BadgeState(val config: BadgeConfig, val suppressed: Boolean)

  private val _stats = MutableStateFlow(StatsState())
  val stats: StateFlow<StatsState> = _stats.asStateFlow()

  private val _refreshing = MutableStateFlow(false)
  val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

  private val _serviceEnabled = MutableStateFlow(ServiceStatus.isEnabled(application))
  val serviceEnabled: StateFlow<Boolean> = _serviceEnabled.asStateFlow()

  private val _onboardingSkipped = MutableStateFlow(prefs.onboardingSkipped)
  val onboardingSkipped: StateFlow<Boolean> = _onboardingSkipped.asStateFlow()

  private val _period = MutableStateFlow(Period.DAILY)
  val period: StateFlow<Period> = _period.asStateFlow()

  private val _badge = MutableStateFlow(BadgeState(prefs.badgeConfig, prefs.badgeSuppressed))
  val badge: StateFlow<BadgeState> = _badge.asStateFlow()

  private val _tracked = MutableStateFlow(prefs.trackedPackages)
  val tracked: StateFlow<Set<String>> = _tracked.asStateFlow()

  // Strong reference: SharedPreferences only holds listeners weakly.
  private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
    _badge.value = BadgeState(prefs.badgeConfig, prefs.badgeSuppressed)
    _tracked.value = prefs.trackedPackages
    _onboardingSkipped.value = prefs.onboardingSkipped
  }

  init {
    prefs.raw.registerOnSharedPreferenceChangeListener(prefListener)
    viewModelScope.launch {
      // Reload whenever the service (or a debug action) writes; bursts coalesce.
      DataEvents.version.collectLatest {
        delay(RELOAD_DEBOUNCE_MS)
        reload()
      }
    }
  }

  override fun onCleared() {
    prefs.raw.unregisterOnSharedPreferenceChangeListener(prefListener)
  }

  // region Stats

  private suspend fun reload() {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val rows = withContext(Dispatchers.IO) {
      runCatching { store.hourRows(Stats.rangeStartMs(today, zone), Stats.rangeEndMs(today, zone)) }.getOrDefault(emptyList())
    }
    _stats.value = StatsState(rows, today, loaded = true)
  }

  fun refresh() {
    viewModelScope.launch {
      _refreshing.value = true
      reload()
      _refreshing.value = false
    }
  }

  /** Called on every ON_RESUME: settings may have changed and the day may have rolled over. */
  fun onResume() {
    refreshServiceStatus()
    refresh()
  }

  fun setPeriod(p: Period) {
    _period.value = p
  }

  // endregion

  // region Service / onboarding

  fun refreshServiceStatus() {
    _serviceEnabled.value = ServiceStatus.isEnabled(getApplication())
  }

  fun openAccessibilitySettings() = ServiceStatus.openAccessibilitySettings(getApplication())

  fun openAppDetails() = ServiceStatus.openAppDetails(getApplication())

  fun setOnboardingSkipped(skipped: Boolean) {
    prefs.onboardingSkipped = skipped
    _onboardingSkipped.value = skipped
  }

  // endregion

  // region Settings

  fun setTracked(packageName: String, on: Boolean) {
    val next = if (on) _tracked.value + packageName else _tracked.value - packageName
    prefs.trackedPackages = next
  }

  fun setBadgeEnabled(enabled: Boolean) = prefs.updateBadgeConfig(enabled = enabled)
  fun setBadgeVisibility(v: BadgeVisibility) = prefs.updateBadgeConfig(visibility = v)
  fun setBadgeSize(s: BadgeSize) = prefs.updateBadgeConfig(size = s)
  fun setBadgeOpacity(o: Float) = prefs.updateBadgeConfig(opacity = o)
  fun setBadgeTapAction(a: BadgeTapAction) = prefs.updateBadgeConfig(tapAction = a)
  fun setBadgeShowTimer(show: Boolean) = prefs.updateBadgeConfig(showTimer = show)
  fun resetBadgePosition() = prefs.resetBadgePosition()

  /** null or <= 0 clears the limit. */
  fun setDailyLimit(limit: Int?) = prefs.updateBadgeConfig(dailyLimit = limit ?: 0)

  // endregion

  // region Data

  /** Writes all views to a CSV in cacheDir/exports and returns a share intent, or null when empty. */
  suspend fun exportCsv(): Intent? = withContext(Dispatchers.IO) {
    val rows = store.allAscending()
    if (rows.isEmpty()) return@withContext null
    val dir = File(app.cacheDir, "exports").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val name = "reels-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.csv"
    val file = File(dir, name).apply { writeText(Format.csv(rows, ZoneId.systemDefault())) }
    val uri = FileProvider.getUriForFile(app, "${app.packageName}.exports", file)
    val send = Intent(Intent.ACTION_SEND)
      .setType("text/csv")
      .putExtra(Intent.EXTRA_STREAM, uri)
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    Intent.createChooser(send, "Export reel history")
  }

  fun deleteAllData() {
    viewModelScope.launch(Dispatchers.IO) {
      store.clearAll()
      ReelsAccessibilityService.notifyDataChanged(cleared = true)
      DataEvents.notifyChanged()
    }
  }

  /** Debug builds only: synthetic views so dashboard and badge can be tested without Instagram. */
  fun insertDebugEvents(events: List<Triple<String, Long, Long?>>, done: (Int) -> Unit) {
    viewModelScope.launch {
      withContext(Dispatchers.IO) { store.insertMany(events) }
      ReelsAccessibilityService.notifyDataChanged(cleared = false)
      DataEvents.notifyChanged()
      done(events.size)
    }
  }

  suspend fun nativeTodayTotals(): Pair<Int, Long> = withContext(Dispatchers.IO) {
    val zone = ZoneId.systemDefault()
    val t = store.totalsSince(LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli())
    t.count to t.durationMs
  }

  // endregion

  companion object {
    private const val RELOAD_DEBOUNCE_MS = 300L
  }
}
