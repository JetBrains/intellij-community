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
import com.intellij.util.asDisposable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

internal class CompositeFilterWrapper(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val filtersUpdatedListeners: MutableList<() -> Unit> = CopyOnWriteArrayList()

  private val filtersComputationInProgress: AtomicBoolean = AtomicBoolean(false)

  @Volatile
  private var cachedFilter: CompositeFilter? = null

  @Volatile
  private var filtersComputed: CompletableDeferred<Unit> = CompletableDeferred()

  init {
    ConsoleFilterProvider.FILTER_PROVIDERS.addChangeListener({
                                                               cachedFilter = null
                                                               filtersComputed = CompletableDeferred()
                                                               scheduleFiltersComputation()
                                                             }, coroutineScope.asDisposable())
    scheduleFiltersComputation()
  }

  private fun scheduleFiltersComputation() {
    if (filtersComputationInProgress.compareAndSet(false, true)) {
      coroutineScope.launch {
        val filters = readAction {
          ConsoleViewUtil.computeConsoleFilters(project, null, GlobalSearchScope.allScope(project))
        }
        filtersComputationInProgress.set(false)
        cachedFilter = CompositeFilter(project, filters).also {
          it.setForceUseAllFilters(true)
        }
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          fireFiltersUpdated()
          filtersComputed.complete(Unit)
        }
      }
    }
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
    scheduleFiltersComputation()
    return null
  }

  @TestOnly
  internal suspend fun awaitFiltersComputed() {
    filtersComputed.await()
  }
}
