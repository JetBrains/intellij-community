// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.codeInsight.mlcompletion.PyCompletionMlElementInfo
import com.jetbrains.python.codeInsight.mlcompletion.PyCompletionMlElementKind
import com.jetbrains.python.psi.*

object PyCompletionFeatures {
  fun isDirectlyInArgumentsContext(locationPsi: PsiElement): Boolean {
    // for zero prefix
    if (locationPsi.parent is PyArgumentList) return true

    // for non-zero prefix
    if (locationPsi.parent !is PyReferenceExpression) return false
    if (locationPsi.parent.parent !is PyArgumentList) return false

    return true
  }

  private fun isAfterColon(locationPsi: PsiElement): Boolean {
    val prevVisibleLeaf = PsiTreeUtil.prevVisibleLeaf(locationPsi)
    return (prevVisibleLeaf != null && prevVisibleLeaf.elementType == PyTokenTypes.COLON)
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
    val whitespaceElem = when {
      isIndentElement(locationPsi) -> locationPsi
      locationPsi.prevSibling != null && isIndentElement(locationPsi.prevSibling) -> locationPsi.prevSibling
      else -> return res
    }
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

  fun getNumberOfOccurrencesInScope(kind: PyCompletionMlElementKind, locationPsi: PsiElement, lookupString: String): Int? {
    when (kind) {
      in arrayOf(PyCompletionMlElementKind.FUNCTION,
                 PyCompletionMlElementKind.TYPE_OR_CLASS,
                 PyCompletionMlElementKind.FROM_TARGET) ->
      {
        val statementList = PsiTreeUtil.getParentOfType(locationPsi, PyStatementList::class.java, PyFile::class.java) ?: return null
        val children = PsiTreeUtil.collectElementsOfType(statementList, PyReferenceExpression::class.java)
        return children.count { it.textOffset < locationPsi.textOffset && it.textMatches(lookupString) }
      }
      PyCompletionMlElementKind.NAMED_ARG -> {
        val psiArgList = PsiTreeUtil.getParentOfType(locationPsi, PyArgumentList::class.java) ?: return null
        val children = PsiTreeUtil.getChildrenOfType(psiArgList, PyKeywordArgument::class.java) ?: return null
        return children.map { it.firstChild }.count {
          lookupString == "${it.text}="
        }
      }
      PyCompletionMlElementKind.PACKAGE_OR_MODULE -> {
        val imports = PsiTreeUtil.collectElementsOfType(locationPsi.containingFile, PyImportElement::class.java)
        return imports.count { imp ->
          val refExpr = imp.importReferenceExpression
          refExpr != null && refExpr.textMatches(lookupString)
        }
      }
      else -> {
        return null
      }
    }
  }

  fun getImportPopularityFeature(locationPsi: PsiElement, lookupString: String): Int? {
    if (locationPsi.parent !is PyReferenceExpression) return null
    if (locationPsi.parent.parent !is PyImportElement) return null
    if (locationPsi.parent.parent.parent !is PyImportStatement) return null
    return PyMlCompletionHelpers.importPopularity[lookupString]
  }

  fun getBuiltinPopularityFeature(lookupString: String, isBuiltins: Boolean): Int? =
    if (isBuiltins) PyMlCompletionHelpers.builtinsPopularity[lookupString] else null

  fun getKeywordId(lookupString: String): Int? = PyMlCompletionHelpers.getKeywordId(lookupString)

  fun getPyLookupElementInfo(element: LookupElement): PyCompletionMlElementInfo? = element.getUserData(
    PyCompletionMlElementInfo.key)
}