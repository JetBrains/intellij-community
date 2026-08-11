// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.ReferencesCodeVisionProvider
import com.intellij.openapi.options.advanced.AdvancedSettings.Companion.getInt
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.PsiSearchHelper.SearchCostResult
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import com.jetbrains.python.PyBundle
import com.jetbrains.python.ast.impl.PyUtilCore
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyImportStatementBase
import com.jetbrains.python.psi.PyUtil
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Test-only override for the widespread-search budget; the timing is otherwise machine-dependent. */
@TestOnly
var pyUsagesSearchBudgetTestOverride: Duration? = null

class PyReferencesCodeVisionProvider : ReferencesCodeVisionProvider() {
  companion object {
    const val ID = "python.references"

    // When a symbol's name is too widespread for a cheap exact count, ReferencesSearch could end up
    // scanning the whole project (worst case: a common-word name with few real references). For such
    // names we cap the search by this wall-clock budget and render the partial count as "N+",
    // instead of suppressing the hint entirely as before (PY-82336).
    private val WIDESPREAD_SEARCH_BUDGET = 100.milliseconds
  }

  override fun acceptsFile(file: PsiFile): Boolean = file is PyFile

  override fun acceptsElement(element: PsiElement): Boolean {
    if (!element.manager.isInProject(element)) return false

    if (element is PyClass && PyUtil.isTopLevel(element)) {
      return true
    }

    if (element is PyFunction) {
      if (!PyUtil.isTopLevel(element)) {
        val containingClass = element.containingClass
        if (containingClass == null || !PyUtil.isTopLevel(containingClass)) return false
      }
      val elementName = element.name ?: return false
      return !PyUtilCore.isSpecialName(elementName)
    }

    return false
  }

  override fun getVisionInfo(element: PsiElement, file: PsiFile): CodeVisionInfo? {
    if (element !is PsiNamedElement) return null
    val elementName = element.name ?: return null

    val project = element.project
    val scope = GlobalSearchScope.projectScope(project)
    // A non-positive advanced-setting value means "no cap"; guard against it so the hint never
    // degrades to "no usages" when the setting is misconfigured or unavailable.
    val configuredLimit = getInt("python.code.vision.usages.limit")
    val maxUsagesToCount = if (configuredLimit > 0) configuredLimit else Int.MAX_VALUE

    // If the name is widespread, the search could be expensive, so bound it by time and render "N+".
    // For cheap names we keep an exact, deterministic count (no time budget, so results don't flicker).
    val isWidespreadName = PsiSearchHelper.getInstance(project).isCheapEnoughToSearch(elementName, scope, file) ==
                     SearchCostResult.TOO_MANY_OCCURRENCES
    val budget = pyUsagesSearchBudgetTestOverride ?: WIDESPREAD_SEARCH_BUDGET
    val deadline = if (isWidespreadName) TimeSource.Monotonic.markNow() + budget else null

    val regularUsages = AtomicInteger()
    val dynamicUsages = AtomicInteger()
    val isTruncated = AtomicBoolean()

    ReferencesSearch.search(ReferencesSearch.SearchParameters(element, scope, false))
      .allowParallelProcessing()
      .forEach(Processor {
        if (it == null) true
        else if (element.reference == it) true
        else if (PsiTreeUtil.getParentOfType(it.element, PyImportStatementBase::class.java) != null) true // imports are not usages
        else {
          if (UsageInfo(it).isDynamicUsage) dynamicUsages.incrementAndGet()
          val count = regularUsages.incrementAndGet()
          if (count > maxUsagesToCount || deadline?.hasPassedNow() == true) {
            isTruncated.set(true)
            false
          }
          else true
        }
      })

    val result = regularUsages.get()
    val dynamicResult = dynamicUsages.get()
    if (result == 0 && dynamicResult == 0) return null
    val hasMoreUsages = isTruncated.get()
    if (dynamicResult == 0 || hasMoreUsages) {
      return CodeVisionInfo(PyBundle.message("inlay.hints.usages.text", min(result, maxUsagesToCount), if (hasMoreUsages) 1 else 0),
                            result, !hasMoreUsages)
    }
    return CodeVisionInfo(PyBundle.message("inlay.hints.usages.with.dynamic.text", result, dynamicResult), result)
  }

  override fun getHint(element: PsiElement, file: PsiFile): String? {
    return getVisionInfo(element, file)?.text
  }

  override fun logClickToFUS(element: PsiElement, hint: String) {
    PyCodeVisionUsageCollector.logClickToFUS(element)
  }

  override val relativeOrderings: List<CodeVisionRelativeOrdering>
    get() = emptyList()
  override val id: String
    get() = ID
}