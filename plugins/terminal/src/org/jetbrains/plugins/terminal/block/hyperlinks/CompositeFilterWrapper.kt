// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.hyperlinks

import com.intellij.execution.filters.CompositeFilter
import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.openapi.application.*
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

private data class ComputedFilter(
  val filter: CompositeFilter,
  val listenersFired: Boolean,
)

@ApiStatus.Internal
class CompositeFilterWrapper(private val project: Project, coroutineScope: CoroutineScope) {
  private val filtersUpdatedListeners: MutableList<() -> Unit> = CopyOnWriteArrayList()

  private val customFilters: MutableList<Filter> = CopyOnWriteArrayList()

  private val computationInitialized = AtomicBoolean()

  private val filterComputationRequests = MutableSharedFlow<Unit>(
    replay = 1, // ensures that the first update isn't lost even if it's emitted before the collector starts working
    onBufferOverflow = BufferOverflow.DROP_OLDEST, // ensures non-blocking tryEmit that always succeeds
  )

  private val filterFlow = MutableStateFlow<ComputedFilter?>(null)

  init {
    ConsoleFilterProvider.FILTER_PROVIDERS.addChangeListener(coroutineScope, ::rescheduleFilterComputation)
    coroutineScope.launch { 
      filterComputationRequests.collectLatest { 
        filterFlow.value = null // tell the clients the value is being computed
        val newValue = ComputedFilter(computeFilter(), false)
        filterFlow.value = newValue
        // Using UiWithModelAccess because the listeners interact with the editor and its document,
        // so they need to take locks, and therefore the strict dispatcher won't do.
        withContext(Dispatchers.UiWithModelAccess + ModalityState.any().asContextElement()) {
          fireFiltersUpdated()
          filterFlow.value = newValue.copy(listenersFired = true)
        }
      }
    }
  }

  private suspend fun computeFilter(): CompositeFilter {
    val filters = readAction {
      ConsoleViewUtil.computeConsoleFilters(project, null, GlobalSearchScope.allScope(project))
    }
    return CompositeFilter(project, customFilters + filters).also {
      it.setForceUseAllFilters(true)
    }
  }

  private fun fireFiltersUpdated() {
    for (listener in filtersUpdatedListeners) {
      listener()
    }
  }

  fun addFilter(filter: Filter) {
    customFilters.add(filter)
    rescheduleFilterComputation()
  }

  fun addFiltersUpdatedListener(listener: () -> Unit) {
    filtersUpdatedListeners.add(listener)
  }

  /**
   * @return [Filter] instance if cached. Otherwise, returns `null` and starts computing filters in background;
   *         when filters are ready, `filtersUpdated` event will be fired.
   */
  fun getFilter(): CompositeFilter? {
    filterFlow.value?.filter?.let {
      return it
    }
    ensureComputationInitialized()
    return null
  }

  /**
   * Returns the flow of computed filters.
   */
  fun getFilterFlow(): Flow<CompositeFilter> {
    ensureComputationInitialized()
    return filterFlow.mapNotNull { it?.filter }.distinctUntilChanged()
  }

  private fun ensureComputationInitialized() {
    if (computationInitialized.compareAndSet(false, true)) {
      scheduleFilterComputation()
    }
  }

  private fun rescheduleFilterComputation() {
    if (computationInitialized.get()) {
      scheduleFilterComputation()
    }
  }

  private fun scheduleFilterComputation() {
    check(filterComputationRequests.tryEmit(Unit))
  }

  @TestOnly
  internal suspend fun awaitFiltersComputed() {
    ensureComputationInitialized()
    filterFlow.first { it != null && it.listenersFired }
  }
}
