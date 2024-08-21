// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.google.common.collect.ImmutableRangeSet
import com.google.common.collect.Range
import com.intellij.openapi.util.Version
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.ast.*
import com.jetbrains.python.ast.impl.PyPsiUtilsCore
import org.jetbrains.annotations.ApiStatus
import java.math.BigInteger

@ApiStatus.Internal
object PyVersionCheck {
  /**
   * @return Version ranges if {@code expression} is a version check, {@code null} otherwise
   *
   * @see <a href="https://peps.python.org/pep-0484/#version-and-platform-checking">Version and Platform Checks</a>
   */
  @JvmStatic
  fun convertToVersionRanges(expression: PyAstExpression): ImmutableRangeSet<Version>? {
    val binaryExpr = PyPsiUtilsCore.flattenParens(expression) as? PyAstBinaryExpression ?: return null
    when (val operator = binaryExpr.operator) {
      PyTokenTypes.AND_KEYWORD, PyTokenTypes.OR_KEYWORD -> {
        val rhs = binaryExpr.rightExpression ?: return null
        val ranges1 = convertToVersionRanges(binaryExpr.leftExpression) ?: return null
        val ranges2 = convertToVersionRanges(rhs) ?: return null
        return if (operator === PyTokenTypes.AND_KEYWORD)
          ranges1.intersection(ranges2)
        else
          ranges1.union(ranges2)
      }

      PyTokenTypes.LT, PyTokenTypes.GT, PyTokenTypes.LE, PyTokenTypes.GE -> {
        val refExpr = PyPsiUtilsCore.flattenParens(binaryExpr.leftExpression) as? PyAstReferenceExpression ?: return null
        if (SYS_VERSION_INFO_QUALIFIED_NAME != refExpr.asQualifiedName()) return null

        val tuple = PyPsiUtilsCore.flattenParens(binaryExpr.rightExpression) as? PyAstTupleExpression<*> ?: return null
        val version = evaluateVersion(tuple) ?: return null

        val range = when (operator) {
          PyTokenTypes.LT -> Range.lessThan(version)
          PyTokenTypes.GT -> Range.greaterThan(version)
          PyTokenTypes.LE -> Range.atMost(version)
          PyTokenTypes.GE -> Range.atLeast(version)
          else -> throw IllegalStateException()
        }
        return ImmutableRangeSet.of(range)
      }

      else -> return null
    }
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