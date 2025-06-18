// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.hyperlinks

import com.intellij.execution.filters.CompositeFilter
import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.SynchronizedClearableLazy
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CopyOnWriteArrayList

internal class CompositeFilterWrapper(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val filtersUpdatedListeners: MutableList<() -> Unit> = CopyOnWriteArrayList()

  private val customFilters: MutableList<Filter> = CopyOnWriteArrayList()

  @Volatile
  private var cachedFilter: CompositeFilter? = null

  @Volatile
  private var areFiltersInUse: Boolean = false

  private val filterDeferredLazy: SynchronizedClearableLazy<Deferred<CompositeFilter>> = SynchronizedClearableLazy {
    startFilterComputation()
  }

  init {
    ConsoleFilterProvider.FILTER_PROVIDERS.addChangeListener(coroutineScope, ::dropFilter)
  }

  fun addFilter(filter: Filter) {
    customFilters.add(filter)
    dropFilter()
  }

  private fun dropFilter() {
    cachedFilter = null
    filterDeferredLazy.drop()?.cancel("Filters updated")
    if (areFiltersInUse) {
      // If filters have been requested already, there is some text in the editor.
      // This text needs to be reprocessed with the updated filters.
      // Trigger filter recomputation to fire the `filtersUpdated` event.
      filterDeferredLazy.value
    }
  }

  private fun startFilterComputation(): Deferred<CompositeFilter> = coroutineScope.async {
    val filters = readAction {
      ConsoleViewUtil.computeConsoleFilters(project, null, GlobalSearchScope.allScope(project))
    }
    val compositeFilter = CompositeFilter(project, customFilters + filters).also {
      it.setForceUseAllFilters(true)
    }
    cachedFilter = compositeFilter
    if (areFiltersInUse) {
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        fireFiltersUpdated()
      }
    }
    compositeFilter
  }

  fun addFiltersUpdatedListener(listener: () -> Unit) {
    filtersUpdatedListeners.add(listener)
  }

  private fun fireFiltersUpdated() {
    for (listener in filtersUpdatedListeners) {
      listener()
    }
  }

  /**
   * @return [Filter] instance if cached. Otherwise, returns `null` and starts computing filters in background;
   *         when filters are ready, `filtersUpdated` event will be fired.
   */
  fun getFilter(): CompositeFilter? {
    cachedFilter?.let {
      return it
    }
    areFiltersInUse = true
    filterDeferredLazy.value
    return null
  }

  @TestOnly
  internal suspend fun awaitFiltersComputed() {
    filterDeferredLazy.value.await()
  }
}
