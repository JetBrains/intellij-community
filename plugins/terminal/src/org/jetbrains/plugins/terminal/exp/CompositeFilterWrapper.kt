// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.execution.filters.CompositeFilter
import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope

internal class CompositeFilterWrapper(private val project: Project, disposable: Disposable) {

  @Volatile
  private var cachedCompositeFilter: CompositeFilter? = null

  init {
    ConsoleFilterProvider.FILTER_PROVIDERS.addChangeListener({ cachedCompositeFilter = null }, disposable)
  }

  private fun createCompositeFilters(): List<Filter> {
    if (project.isDefault) {
      return emptyList()
    }
    return runReadAction {
      if (project.isDisposed) {
        return@runReadAction emptyList<Filter>()
      }
      ConsoleViewUtil.computeConsoleFilters(project, null, GlobalSearchScope.allScope(project))
    }
  }

  val compositeFilter: CompositeFilter
    get() {
      cachedCompositeFilter?.let {
        return it
      }
      val resultCompositeFilter = CompositeFilter(project, createCompositeFilters()).also {
        it.setForceUseAllFilters(true)
      }
      cachedCompositeFilter = resultCompositeFilter
      return resultCompositeFilter
    }
}
