// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.ReferencesCodeVisionProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.options.advanced.AdvancedSettings.Companion.getInt
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.TextRange
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

/** Test-only override for both budgets below, so a test expecting an exact count can let the search finish. */
@TestOnly
var pyUsagesSearchBudgetTestOverride: Duration? = null

class PyReferencesCodeVisionProvider : ReferencesCodeVisionProvider() {
  companion object {
    const val ID = "python.references"

    // One widespread-name search; its partial count is rendered as "N+"
    private val SINGLE_SEARCH_BUDGET = 100.milliseconds

    /** Shared by all widespread-name searches of one pass, so the per-search budget
     * is not multiplied by the number of declarations */
    private val PASS_BUDGET = 300.milliseconds
  }

  /** `computeForEditor` walks the file on one thread, so no synchronization is needed. */
  private val passBudget = ThreadLocal<PassBudget>()

  private class PassBudget(private var remaining: Duration) {
    fun nextSlice(singleSearchBudget: Duration): Duration = minOf(remaining, singleSearchBudget)

    fun charge(spent: Duration) {
      remaining -= spent
    }
  }

  override fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
    passBudget.set(PassBudget(pyUsagesSearchBudgetTestOverride ?: PASS_BUDGET))
    try {
      return super.computeForEditor(editor, file)
    }
    finally {
      passBudget.remove()
    }
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

    val isWidespreadName = PsiSearchHelper.getInstance(project)
      .isCheapEnoughToSearch(elementName, scope, file) == SearchCostResult.TOO_MANY_OCCURRENCES

    val regularUsages = AtomicInteger()
    val dynamicUsages = AtomicInteger()
    val isTruncated = AtomicBoolean()
    val search = { countUsages(element, scope, maxUsagesToCount, regularUsages, dynamicUsages, isTruncated) }

    if (!isWidespreadName) {
      search()
    }
    else {
      val singleSearchBudget = pyUsagesSearchBudgetTestOverride ?: SINGLE_SEARCH_BUDGET
      val budget = passBudget.get()
      val slice = budget?.nextSlice(singleSearchBudget) ?: singleSearchBudget
      if (slice <= Duration.ZERO) return null

      val startedAt = TimeSource.Monotonic.markNow()
      // The deadline has to be enforced by cancellation rather than checked from the result processor: the
      // processor is not called while the search walks files that contain no match, which is exactly the
      // case this budget exists for. `ReferencesSearch` calls `checkCanceled` often enough for the
      // indicator `withTimeout` cancels to stop it. The counters are updated in place, so a timed-out
      // search still contributes what it managed to find.
      val completed = ProgressIndicatorUtils.withTimeout(slice.inWholeMilliseconds) { search(); true } != null
      budget?.charge(startedAt.elapsedNow())
      if (!completed) isTruncated.set(true)
    }

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

  private fun countUsages(
    element: PsiNamedElement,
    scope: GlobalSearchScope,
    maxUsagesToCount: Int,
    regularUsages: AtomicInteger,
    dynamicUsages: AtomicInteger,
    isTruncated: AtomicBoolean,
  ) {
    ReferencesSearch.search(ReferencesSearch.SearchParameters(element, scope, false))
      .allowParallelProcessing()
      .forEach(Processor {
        if (it == null) true
        else if (element.reference == it) true
        else if (PsiTreeUtil.getParentOfType(it.element, PyImportStatementBase::class.java) != null) true // imports are not usages
        else {
          if (UsageInfo(it).isDynamicUsage) dynamicUsages.incrementAndGet()
          if (regularUsages.incrementAndGet() > maxUsagesToCount) {
            isTruncated.set(true)
            false
          }
          else true
        }
      })
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