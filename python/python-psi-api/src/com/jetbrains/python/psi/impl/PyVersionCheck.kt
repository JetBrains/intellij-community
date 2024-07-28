// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.openapi.util.Version
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.ast.*
import com.jetbrains.python.ast.impl.PyPsiUtilsCore
import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.ApiStatus
import java.math.BigInteger

@ApiStatus.Internal
data class PyVersionCheck(val version: Version, val isLessThan: Boolean) {
  fun matches(languageLevel: LanguageLevel): Boolean {
    return isLessThan == languageLevel.isLessThan(version)
  }

  companion object {
    /**
     * Extracts the Python version comparison from {@code ifPart}'s condition if it's a version check as specified in
     * <a href="https://typing.readthedocs.io/en/latest/source/stubs.html#version-and-platform-checks">Version and Platform Checks</a> E.g.
     * <pre>{@code
     * if sys.version_info >= (3,):
     *   ...
     * }</pre>
     * @return A {@link VersionCheck} instance if {@code ifPart} is a (valid) version check, or {@code null} otherwise.
     */
    @JvmStatic
    fun fromCondition(ifPart: PyAstIfPart): PyVersionCheck? {
      val binaryExpr = PyPsiUtilsCore.flattenParens(ifPart.condition)
      if (binaryExpr !is PyAstBinaryExpression) return null

      val lhsRefExpr = PyPsiUtilsCore.flattenParens(binaryExpr.leftExpression)
      if (lhsRefExpr !is PyAstReferenceExpression) return null
      if (SYS_VERSION_INFO_QUALIFIED_NAME != lhsRefExpr.asQualifiedName()) return null

      val versionTuple = PyPsiUtilsCore.flattenParens(binaryExpr.rightExpression)
      if (versionTuple !is PyAstTupleExpression<*>) return null
      val version = evaluateVersion(versionTuple)
      if (version == null) return null

      val operator = binaryExpr.getOperator()
      if (operator !== PyTokenTypes.LT && operator !== PyTokenTypes.GE) return null
      return PyVersionCheck(version, operator === PyTokenTypes.LT)
    }

    private val SYS_VERSION_INFO_QUALIFIED_NAME = QualifiedName.fromDottedString("sys.version_info")

    private fun evaluateVersion(versionTuple: PyAstTupleExpression<*>): Version? {
      val elements = versionTuple.elements
      if (elements.size != 1 && elements.size != 2) {
        return null
      }

      val major = evaluateNumber(elements[0])
      if (major == null) {
        return null
      }

      if (elements.size == 1) {
        return Version(major, 0, 0)
      }

      val minor = evaluateNumber(elements[1])
      if (minor == null) {
        return null
      }

      return Version(major, minor, 0)
    }

    private fun evaluateNumber(expression: PyAstExpression?): Int? {
      if (expression !is PyAstNumericLiteralExpression) return null
      if (!expression.isIntegerLiteral) return null
      val value = expression.bigIntegerValue
      val intValue = value.toInt()
      return if (BigInteger.valueOf(intValue.toLong()) == value) intValue else null
    }
  }
}

fun LanguageLevel.isLessThan(version: Version): Boolean {
  return version.compareTo(majorVersion, minorVersion) > 0
}
