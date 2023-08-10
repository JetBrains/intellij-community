// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import com.jetbrains.python.PyBundle
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyUtil
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

class PyReferencesCodeVisionProvider : ReferencesCodeVisionProvider() {
  companion object {
    const val ID = "python.references"
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
      return !PyUtil.isSpecialName(elementName)
    }

    return false
  }

  override fun getVisionInfo(element: PsiElement, file: PsiFile): CodeVisionInfo? {
    if (element !is PsiNamedElement) return null
    val elementName = element.name ?: return null

    val scope = GlobalSearchScope.projectScope(element.project)
    val costSearchOutsideCurrentFile =
      PsiSearchHelper.getInstance(element.project).isCheapEnoughToSearch(elementName, scope, file, null)
    if (costSearchOutsideCurrentFile == SearchCostResult.TOO_MANY_OCCURRENCES) return null

    val usagesCount = AtomicInteger()
    val dynamicUsagesCount = AtomicInteger()
    val limit = getInt("python.code.vision.usages.limit")

    ReferencesSearch.search(ReferencesSearch.SearchParameters(element, scope, false))
      .allowParallelProcessing()
      .forEach(Processor {
        if (it == null) true
        else if (element.reference == it) true
        else {
          if (UsageInfo(it).isDynamicUsage) dynamicUsagesCount.incrementAndGet()
          usagesCount.incrementAndGet() <= limit
        }
      })

    val result = usagesCount.get()
    val dynamicResult = dynamicUsagesCount.get()
    if (result == 0 && dynamicResult == 0) return null
    if (dynamicResult == 0 || result > limit) {
      return CodeVisionInfo(PyBundle.message("inlay.hints.usages.text", min(result, limit), if (result > limit) 1 else 0),
                            result, result <= limit)
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