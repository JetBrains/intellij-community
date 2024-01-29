// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.hyperlinks

import com.intellij.execution.filters.CompositeFilter
import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

internal class CompositeFilterWrapper(private val project: Project, private val disposable: Disposable) {
  private val filtersUpdatedListeners: MutableList<() -> Unit> = CopyOnWriteArrayList()

  private val filtersComputationInProgress: AtomicBoolean = AtomicBoolean(false)
  @Volatile
  private var cachedFilter: CompositeFilter? = null

  init {
    ConsoleFilterProvider.FILTER_PROVIDERS.addChangeListener({
                                                               cachedFilter = null
                                                               scheduleFiltersComputation()
                                                             }, disposable)
    scheduleFiltersComputation()
  }

  private fun scheduleFiltersComputation() {
    if (filtersComputationInProgress.compareAndSet(false, true)) {
      ReadAction
        .nonBlocking<List<Filter>> { ConsoleViewUtil.computeConsoleFilters(project, null, GlobalSearchScope.allScope(project)) }
        .expireWith(disposable)
        .finishOnUiThread(ModalityState.defaultModalityState()) { filters: List<Filter> ->
          filtersComputationInProgress.set(false)
          cachedFilter = CompositeFilter(project, filters).also {
            it.setForceUseAllFilters(true)
          }
          fireFiltersUpdated()
        }.submit(AppExecutorUtil.getAppExecutorService())
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
   *
   */
  fun getFilter(): CompositeFilter? {
    cachedFilter?.let {
      return it
    }
    scheduleFiltersComputation()
    return null
  }
}
