// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.mlcompletion

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.MLFeatureValue
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

  fun getElementPsiLocationFeatures(element: LookupElement, location: CompletionLocation): Map<String, MLFeatureValue> {
    val caretPsiPosition = location.completionParameters.position
    val elementPsiPosition = element.psiElement ?: return emptyMap()

    val caretFile = caretPsiPosition.containingFile?.originalFile ?: return emptyMap()
    val elementFile = elementPsiPosition.containingFile?.originalFile ?: return emptyMap()
    if (caretFile != elementFile) return emptyMap()

    val result = mutableMapOf(
      "is_the_same_file" to MLFeatureValue.binary(true),
      "text_offset_distance" to MLFeatureValue.numerical(caretPsiPosition.textOffset - elementPsiPosition.textOffset)
    )

    if (isTheSameScope(caretPsiPosition, elementPsiPosition, PyClass::class.java)) {
      result["is_the_same_class"] = MLFeatureValue.binary(true)
    }

    if (isTheSameScope(caretPsiPosition, elementPsiPosition, PyFunction::class.java)) {
      result["is_the_same_method"] = MLFeatureValue.binary(true)
    }

    return result
  }

  private fun <T: PsiElement> isTheSameScope(caretPsiPosition: PsiElement, elementPsiPosition: PsiElement, scopeClass: Class<T>): Boolean {
    val caretEnclosingScope = PsiTreeUtil.getParentOfType(caretPsiPosition, scopeClass) ?: return false
    val elementEnclosingScope = PsiTreeUtil.getParentOfType(elementPsiPosition, scopeClass) ?: return false
    return isOriginalElementsTheSame(caretEnclosingScope, elementEnclosingScope)
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

  fun getNumberOfOccurrencesInScope(kind: PyCompletionMlElementKind, contextFeatures: ContextFeatures, lookupString: String): Int {
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

  private fun isAfterColon(locationPsi: PsiElement): Boolean {
    val prevVisibleLeaf = PsiTreeUtil.prevVisibleLeaf(locationPsi)
    return (prevVisibleLeaf != null && prevVisibleLeaf.elementType == PyTokenTypes.COLON)
  }

  private fun isOriginalElementsTheSame(a: PsiElement, b: PsiElement) =
    CompletionUtil.getOriginalElement(a) == CompletionUtil.getOriginalElement(b)
}