package dev.reelscounter.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-process "the data changed" signal. The service bumps it after every
 * write; screens collect it and reload. It carries no data: the database is
 * the single source of truth.
 */
object DataEvents {
  private val _version = MutableStateFlow(0L)
  val version: StateFlow<Long> = _version.asStateFlow()

  fun notifyChanged() {
    _version.update { it + 1 }
  }
}
