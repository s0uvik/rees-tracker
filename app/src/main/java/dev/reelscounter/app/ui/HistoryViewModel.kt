package dev.reelscounter.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.reelscounter.app.ReelsApp
import dev.reelscounter.app.data.DataEvents
import dev.reelscounter.app.data.ReelEventStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keyset-paginated, newest-first list of views; reloads its first page on every data change. */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
  private val store = (application as ReelsApp).store

  data class State(val rows: List<ReelEventStore.Row> = emptyList(), val loading: Boolean = true, val endReached: Boolean = false)

  private val _state = MutableStateFlow(State())
  val state: StateFlow<State> = _state.asStateFlow()
  private var loadingMore = false

  init {
    viewModelScope.launch {
      DataEvents.version.collectLatest {
        delay(300)
        loadFirst()
      }
    }
  }

  private suspend fun loadFirst() {
    val page = withContext(Dispatchers.IO) { store.page(PAGE) }
    _state.value = State(page, loading = false, endReached = page.size < PAGE)
  }

  fun reload() {
    viewModelScope.launch { loadFirst() }
  }

  fun loadMore() {
    val s = _state.value
    val last = s.rows.lastOrNull() ?: return
    if (s.endReached || loadingMore) return
    loadingMore = true
    viewModelScope.launch {
      try {
        val page = withContext(Dispatchers.IO) { store.page(PAGE, after = last) }
        _state.value = _state.value.copy(rows = _state.value.rows + page, endReached = page.size < PAGE)
      } finally {
        loadingMore = false
      }
    }
  }

  companion object {
    private const val PAGE = 100
  }
}
