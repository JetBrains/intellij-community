// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyPsiUtils
import com.jetbrains.python.pyi.PyiFile
import com.jetbrains.python.sdk.PythonSdkUtil

class PyTypeShedConditionChecker(
  private var myPythonSdk: Sdk,
  private var myLanguageLevel: LanguageLevel,
  ) {

  private val myIsRemoteSdk: Boolean by lazy { PythonSdkUtil.isRemote(myPythonSdk) }

  /**
   * Checks if an element is located inside a pyi-file and all outer if-statements, if any, are satisfied.
   * Only conditions like `if sys.version_info >= (3, 8)` and `if sys.platform == "win32"` are supported.
   *
   * @see [stub-versioning][https://github.com/python/typeshed/blob/main/CONTRIBUTING.md#stub-versioning]
   *
   * @param element the element to check
   * @return `null` in case the [element] is not located inside a pyi-file or some of the outer if-statements
   * could not be evaluated because of unsupported conditions.
   * Returns `true` if all outer if-statements, if any, are satisfied, `false` otherwise.
   */
  fun allOuterIfStatementsAreSatisfied(element: PsiElement): Boolean? {
    if (element !is PyElement) return null
    return if (element.containingFile is PyiFile) outerIfStatementsAreSatisfied(element) else null
  }

  private fun outerIfStatementsAreSatisfied(element: PyElement): Boolean? {
    val elementParentPart = PsiTreeUtil.getParentOfType(element, PyIfPart::class.java, PyElsePart::class.java) ?: return true
    val ifStmnt = PsiTreeUtil.getParentOfType(elementParentPart, PyIfStatement::class.java) ?: return null

    val ifPart = ifStmnt.getIfPart()
    var ok = isConditionSatisfied(ifPart.condition) ?: return null
    if (ifPart !== elementParentPart && ok) return false
    if (ifPart === elementParentPart) {
      return if(ok) outerIfStatementsAreSatisfied(ifStmnt) else false
    }

    for (elifPart in ifStmnt.elifParts) {
      ok = isConditionSatisfied(elifPart.condition) ?: return null
      if (elifPart !== elementParentPart && ok) return false
      if (elifPart === elementParentPart) {
        return if(ok) outerIfStatementsAreSatisfied(ifStmnt) else false
      }
    }

    // else part has no condition to check - continue with outer part
    return outerIfStatementsAreSatisfied(ifStmnt)
  }

  private fun isConditionSatisfied(condition: PyExpression?): Boolean? {
    val con = PyPsiUtils.flattenParens(condition) ?: return null
    if (con !is PyBinaryExpression || con.rightExpression == null) return null

    val operator: PyElementType = con.operator ?: return null
    val lhs = PyPsiUtils.flattenParens(con.leftExpression) ?: return null

    if (lhs is PyBinaryExpression) {
      if (operator == PyTokenTypes.AND_KEYWORD || operator == PyTokenTypes.OR_KEYWORD) {
        val lhsOK = isConditionSatisfied(lhs) ?: return null
        return when(operator) {
          PyTokenTypes.AND_KEYWORD -> {
            if (!lhsOK) false else isConditionSatisfied(con.rightExpression)
          }
          PyTokenTypes.OR_KEYWORD -> {
            if (lhsOK) true else isConditionSatisfied(con.rightExpression)
          }
          else -> null
        }
      }
    }

    lhs.text.let {
      if (it == "sys.version_info" || it == "sys.platform") {
        val rhs = PyPsiUtils.flattenParens(con.rightExpression) ?: return null
        return when (it) {
          "sys.version_info" -> isPythonVersionCheckSatisfied(operator, rhs)
          "sys.platform" -> isPlatformCheckSatisfied(operator, rhs)
          else -> null
        }
      }
    }

    return null
  }

   private fun isPlatformCheckSatisfied(operator: PyElementType, expression: PyExpression): Boolean? {
    if (myIsRemoteSdk || expression !is PyStringLiteralExpression) return null

    return when(operator) {
      PyTokenTypes.EQEQ -> {
        when (expression.stringValue) {
          "win32" -> SystemInfo.isWindows
          "linux" -> SystemInfo.isLinux
          "darwin" -> SystemInfo.isMac
          else -> null
        }
      }
      PyTokenTypes.NE -> {
        when (expression.stringValue) {
          "win32" -> !SystemInfo.isWindows
          "linux" -> !SystemInfo.isLinux
          "darwin" -> !SystemInfo.isMac
          else -> null
        }
      }
      else -> null
    }
  }

  private fun isPythonVersionCheckSatisfied(operator: PyElementType, expression: PyExpression): Boolean? {
    if (expression !is PyTupleExpression) return null

    val levelToCheck = expression.elements.let {
      if (it.size != 2) return null
      LanguageLevel.fromPythonVersion(it.joinToString(".") { e -> e.text }) ?: return null
    }

    return when(operator) {
      // myLanguageLevel < levelToCheck
      PyTokenTypes.LT -> myLanguageLevel.isOlderThan(levelToCheck)
      // myLanguageLevel > levelToCheck
      PyTokenTypes.GT -> levelToCheck.isOlderThan(myLanguageLevel)
      // myLanguageLevel <= levelToCheck
      PyTokenTypes.LE -> levelToCheck.isAtLeast(myLanguageLevel)
      // myLanguageLevel >= levelToCheck
      PyTokenTypes.GE -> myLanguageLevel.isAtLeast(levelToCheck)
      // myLanguageLevel == levelToCheck
      PyTokenTypes.EQEQ -> myLanguageLevel == levelToCheck
      // myLanguageLevel != levelToCheck
      PyTokenTypes.NE -> myLanguageLevel != levelToCheck
      else -> null
    }
  }
}