// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.*
import com.jetbrains.python.sdk.PythonSdkUtil

object PyCompletionFeatures {
  fun isDictKey(element: LookupElement): Boolean {
    val presentation = LookupElementPresentation.renderElement(element)
    return ("dict key" == presentation.typeText)
  }

  fun isTheSameFile(element: LookupElement, location: CompletionLocation): Boolean {
    val psiFile = location.completionParameters.originalFile
    val elementPsiFile = element.psiElement?.containingFile ?: return false
    return psiFile == elementPsiFile
  }

  fun isTakesParameterSelf(element: LookupElement): Boolean {
    val presentation = LookupElementPresentation.renderElement(element)
    return presentation.tailText == "(self)"
  }

  enum class ElementNameUnderscoreType {NO_UNDERSCORE, TWO_START_END, TWO_START, ONE_START}
  fun getElementNameUnderscoreType(name: String): ElementNameUnderscoreType {
    return when {
      name.startsWith("__") && name.endsWith("__") -> ElementNameUnderscoreType.TWO_START_END
      name.startsWith("__") -> ElementNameUnderscoreType.TWO_START
      name.startsWith("_") -> ElementNameUnderscoreType.ONE_START
      else -> ElementNameUnderscoreType.NO_UNDERSCORE
    }
  }

  fun isPsiElementIsPyFile(element: LookupElement) = element.psiElement is PyFile

  fun isPsiElementIsPsiDirectory(element: LookupElement) = element.psiElement is PsiDirectory

  data class ElementModuleCompletionFeatures(val isFromStdLib: Boolean, val canFindModule: Boolean)
  fun getElementModuleCompletionFeatures(element: LookupElement): ElementModuleCompletionFeatures? {
    val psiElement = element.psiElement ?: return null
    var vFile: VirtualFile? = null
    var sdk: Sdk? = null
    val containingFile = psiElement.containingFile
    if (psiElement is PsiDirectory) {
      vFile = psiElement.virtualFile
      sdk = PythonSdkUtil.findPythonSdk(psiElement)
    }
    else if (containingFile != null) {
      vFile = containingFile.virtualFile
      sdk = PythonSdkUtil.findPythonSdk(containingFile)
    }
    if (vFile != null) {
      val isFromStdLib = PythonSdkUtil.isStdLib(vFile, sdk)
      val canFindModule = ModuleUtilCore.findModuleForFile(vFile, psiElement.project) != null
      return ElementModuleCompletionFeatures(isFromStdLib, canFindModule)
    }
    return null
  }

  fun isInCondition(locationPsi: PsiElement): Boolean {
    if (isAfterColon(locationPsi)) return false
    val condition = PsiTreeUtil.getParentOfType(locationPsi, PyConditionalStatementPart::class.java, true,
                                                                          PyArgumentList::class.java, PyStatementList::class.java)
    return condition != null
  }

  fun isAfterIfStatementWithoutElseBranch(locationPsi: PsiElement): Boolean {
    val prevKeywords = getPrevKeywordsIdsInTheSameColumn(locationPsi, 1)
    if (prevKeywords.isEmpty()) return false
    val ifKwId = PyMlCompletionHelpers.getKeywordId("if")
    val elifKwId = PyMlCompletionHelpers.getKeywordId("elif")
    return prevKeywords[0] == ifKwId || prevKeywords[0] == elifKwId
  }

  fun isInForStatement(locationPsi: PsiElement): Boolean {
    if (isAfterColon(locationPsi)) return false
    val parent = PsiTreeUtil.getParentOfType(locationPsi, PyForPart::class.java, true, PyStatementList::class.java)
    return parent != null
  }

  fun getPrevNeighboursKeywordIds(locationPsi: PsiElement, maxPrevKeywords: Int = 2): ArrayList<Int> {
    val res = ArrayList<Int>()
    var cur: PsiElement? = locationPsi

    while (cur != null) {
      cur = PsiTreeUtil.prevVisibleLeaf(cur)?: break

      val keywordId = PyMlCompletionHelpers.getKeywordId(cur.text) ?: break
      res.add(keywordId)

      if (res.size >= maxPrevKeywords) break
    }

    return res
  }

  fun getPrevKeywordsIdsInTheSameLine(locationPsi: PsiElement, maxPrevKeywords: Int = 2): ArrayList<Int> {
    val res = ArrayList<Int>()
    var cur: PsiElement? = locationPsi

    while (cur != null) {
      cur = PsiTreeUtil.prevLeaf(cur)?: break
      if (cur is PsiWhiteSpace && cur.textContains('\n')) break

      val keywordId = PyMlCompletionHelpers.getKeywordId(cur.text) ?: continue
      res.add(keywordId)

      if (res.size >= maxPrevKeywords) break
    }

    return res
  }

  fun getPrevKeywordsIdsInTheSameColumn(locationPsi: PsiElement, maxPrevKeywords: Int = 2): ArrayList<Int> {
    val maxSteps = 1000
    fun getIndent(element: PsiElement) = element.text.split('\n').last().length
    fun isIndentElement(element: PsiElement) = element is PsiWhiteSpace && element.textContains('\n')
    fun isInDocstring(element: PsiElement) = element.parent is StringLiteralExpression

    val res = ArrayList<Int>()

    val whitespaceElem = PsiTreeUtil.prevLeaf(locationPsi) ?: return res
    if (!whitespaceElem.text.contains('\n')) return res
    val caretIndent = getIndent(whitespaceElem)

    var stepsCounter = 0
    var cur: PsiElement? = whitespaceElem
    while (cur != null) {
      stepsCounter++
      if (stepsCounter > maxSteps) break

      cur = PsiTreeUtil.prevLeaf(cur)?: break
      val prev = PsiTreeUtil.prevLeaf(cur)?: break
      if (cur is PsiComment || isInDocstring(cur)) continue
      if (!isIndentElement(prev)) continue

      val prevIndent = getIndent(prev)
      if (prevIndent < caretIndent) break
      if (prevIndent > caretIndent) continue

      val keywordId = PyMlCompletionHelpers.getKeywordId(cur.text) ?: break
      res.add(keywordId)

      if (res.size >= maxPrevKeywords) break
      cur = prev
    }

    return res
  }

  fun getNumberOfOccurrencesInScope(kind: PyCompletionMlElementKind, contextFeatures: ContextFeatures, lookupString: String): Int? {
    return when (kind) {
             PyCompletionMlElementKind.FUNCTION,
             PyCompletionMlElementKind.TYPE_OR_CLASS,
             PyCompletionMlElementKind.FROM_TARGET ->
               contextFeatures.getUserData(PyNamesMatchingMlCompletionFeatures.statementListOrFileNamesKey)
                 ?.let { it[lookupString] }
             PyCompletionMlElementKind.NAMED_ARG ->
               contextFeatures.getUserData(PyNamesMatchingMlCompletionFeatures.namedArgumentsNamesKey)
                 ?.let { it[lookupString.substringBefore("=")] }
             PyCompletionMlElementKind.PACKAGE_OR_MODULE ->
               contextFeatures.getUserData(PyNamesMatchingMlCompletionFeatures.importNamesKey)
                 ?.let { it[lookupString] }
             else -> null
           } ?: 0
  }

  fun getBuiltinPopularityFeature(lookupString: String, isBuiltins: Boolean): Int? =
    if (isBuiltins) PyMlCompletionHelpers.builtinsPopularity[lookupString] else null

  fun getKeywordId(lookupString: String): Int? = PyMlCompletionHelpers.getKeywordId(lookupString)

  fun getPyLookupElementInfo(element: LookupElement): PyCompletionMlElementInfo? = element.getUserData(PyCompletionMlElementInfo.key)

  fun getNumberOfQualifiersInExpresionFeature(element: PsiElement): Int {
    if (element !is PyQualifiedExpression) return 1
    return element.asQualifiedName()?.components?.size ?: 1
  }

  private fun isAfterColon(locationPsi: PsiElement): Boolean {
    val prevVisibleLeaf = PsiTreeUtil.prevVisibleLeaf(locationPsi)
    return (prevVisibleLeaf != null && prevVisibleLeaf.elementType == PyTokenTypes.COLON)
  }
}