// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.util.QualifiedName
import com.intellij.util.ProcessingContext
import com.intellij.util.SystemProperties
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.mapArguments
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.resolve.fromFoothold
import com.jetbrains.python.psi.resolve.resolveTopLevelMember
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.TypeEvalContext
import java.util.Locale

/**
 * Contributes file path references for Python string literals where it seems appropriate based on heuristics.
 *
 * References are soft: used only for code completion and ignored during code inspection.
 *
 */
open class PySoftFileReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    val stringLiteral = psiElement(PyStringLiteralExpression::class.java)
    val pattern = psiElement()
      .andOr(stringLiteral.with(HardCodedCalleeName),
             stringLiteral.with(AssignmentMatchingNamePattern),
             stringLiteral.with(KeywordArgumentMatchingNamePattern),
             stringLiteral.with(CallArgumentMatchingParameterNamePattern),
             stringLiteral.with(CallArgumentMatchingParameterType))
    registrar.registerReferenceProvider(pattern, createSoftFileReferenceProvider())
  }

  open fun createSoftFileReferenceProvider(): PsiReferenceProvider = PySoftFileReferenceProvider

  /**
   * Matches string literals used in assignment statements where the assignment target has a name that has something about files or paths.
   */
  private object AssignmentMatchingNamePattern : PatternCondition<PyStringLiteralExpression>("assignmentMatchingPattern") {
    override fun accepts(expr: PyStringLiteralExpression, context: ProcessingContext?): Boolean {
      val assignment = expr.parent as? PyAssignmentStatement ?: return false
      val targetNames = assignment.targets.filterIsInstance<PyTargetExpression>().mapNotNull { it.name }
      return targetNames.any(::matchesPathNamePattern)
    }
  }

  /**
   * Matches string literals used as function arguments where the corresponding function parameter has a name that has something about
   * files or paths.
   */
  private object CallArgumentMatchingParameterNamePattern : PatternCondition<PyStringLiteralExpression>("callArgumentMatchingPattern") {
    override fun accepts(expr: PyStringLiteralExpression, context: ProcessingContext?): Boolean {
      val argList = expr.parent as? PyArgumentList ?: return false
      val callExpr = argList.parent as? PyCallExpression ?: return false
      val typeEvalContext = TypeEvalContext.codeInsightFallback(expr.project)
      val resolveContext = PyResolveContext.defaultContext(typeEvalContext)

      return callExpr.multiResolveCallee(resolveContext)
        .asSequence()
        // Fail-fast check
        .filter { callableType ->
          val parameters = callableType.getParameters(typeEvalContext)
          val parameterNames = parameters?.mapNotNull { it.name } ?: emptyList()
          parameterNames.any(::matchesPathNamePattern)
        }
        .any {
          val mapping = callExpr.mapArguments( it, typeEvalContext)
          val parameterName = mapping.mappedParameters[expr]?.name ?: return@any false
          matchesPathNamePattern(parameterName)
        }
    }
  }

  private object CallArgumentMatchingParameterType : PatternCondition<PyStringLiteralExpression>("callArgumentMatchingPattern") {
    override fun accepts(expr: PyStringLiteralExpression, context: ProcessingContext?): Boolean {
      val argList = expr.parent as? PyArgumentList ?: return false
      val callExpr = argList.parent as? PyCallExpression ?: return false

      val builtinCache = PyBuiltinCache.getInstance(expr)
      val languageLevel = LanguageLevel.forElement(expr)
      val bytesOrUnicodeType = PyUnionType.union(
        listOfNotNull(
          builtinCache.getBytesType(languageLevel),
          builtinCache.getUnicodeType(languageLevel)
        )
      ) ?: return false

      val typeEvalContext = TypeEvalContext.codeInsightFallback(expr.project)

      val osPathLike = resolveTopLevelMember(
        QualifiedName.fromComponents("os", "PathLike"),
        fromFoothold(expr)
      ) as? PyTypedElement ?: return false
      val osPathLikeType = typeEvalContext.getType(osPathLike) ?: return false

      val argumentTypes = callExpr.multiResolveCallee(PyResolveContext.defaultContext(typeEvalContext))
        .asSequence()
        .mapNotNull {
          val mapping = callExpr.mapArguments(it, typeEvalContext)
          mapping.mappedParameters[expr]?.getArgumentType(typeEvalContext)
        }
      
      // We can't use PyTypeChecker.match directly because the type `str | PathLike` is considered incompatible 
      // with neither str nor PathLike (strict union semantics).
      fun PyType.allowsValuesCompatibleWith(superType: PyType): Boolean = 
        PyTypeUtil.toStream(this).anyMatch { it != null && PyTypeChecker.match(superType, it, typeEvalContext) }
      
      return argumentTypes.any { it.allowsValuesCompatibleWith(bytesOrUnicodeType) } &&
             argumentTypes.any { it.allowsValuesCompatibleWith(osPathLikeType) }
    }
  }

  /**
   * Matches string literals used as function keyword arguments where the keyword has a name that has something about files or paths.
   */
  private object KeywordArgumentMatchingNamePattern : PatternCondition<PyStringLiteralExpression>("keywordArgumentMatchingPattern") {
    override fun accepts(expr: PyStringLiteralExpression, context: ProcessingContext?): Boolean {
      val keywordArgument = expr.parent as? PyKeywordArgument ?: return false
      if (keywordArgument.parent?.parent !is PyCallExpression) return false
      return keywordArgument.keyword?.let { matchesPathNamePattern(it) } ?: return false
    }
  }

  /**
   * Matches string literals used as function arguments for the hard-coded set of function known to work with files.
   *
   * It's a last resort where we cannot guess that the string literal is a file path by other means.
   */
  private object HardCodedCalleeName : PatternCondition<PyStringLiteralExpression>("hardCodedCalleeName") {
    private data class Pattern(private val fullName: String, val position: Int, val isBuiltin: Boolean = false) {
      val qualifiedName: QualifiedName = QualifiedName.fromDottedString(fullName)
    }

    private val PATTERNS = listOf(
      Pattern("open", 0, isBuiltin = true), // could be covered by CallArgumentMatchingParameterType in Py3+
      Pattern("os.walk", 0), // could be covered by CallArgumentMatchingParameterType in Py3+
      Pattern("pandas.read_csv", 0)
    )
    private val SIMPLE_NAMES = PATTERNS.associateBy { it.qualifiedName.lastComponent }
    private val QUALIFIED_NAMES = PATTERNS.associateBy { it.qualifiedName }

    override fun accepts(expr: PyStringLiteralExpression, context: ProcessingContext?): Boolean {
      val argList = expr.parent as? PyArgumentList ?: return false
      val callExpr = argList.parent as? PyCallExpression ?: return false
      val callee = callExpr.callee as? PyReferenceExpression ?: return false

      // Fail-fast check
      val simplePattern = SIMPLE_NAMES[callee.name] ?: return false
      if (simplePattern.isBuiltin && argList.arguments.indexOf(expr) == simplePattern.position) return true

      val pattern = PyResolveUtil.resolveImportedElementQNameLocally(callee).map { QUALIFIED_NAMES[it] }.firstOrNull() ?: return false
      return argList.arguments.getOrNull(pattern.position) == expr
    }
  }

  companion object {
    private val FILE_NAME_PATTERNS = linkedSetOf(
      "path",
      "file",
      "filename",
      "filepath",
      "pathname",
      "src",
      "dst",
      "dir"
    )

    private fun matchesPathNamePattern(name: String): Boolean {
      val nameParts = name.split("_")
      return nameParts.any { it.lowercase(Locale.getDefault()) in FILE_NAME_PATTERNS }
    }
  }

  private object PySoftFileReferenceProvider : PsiReferenceProvider() {
    override fun acceptsTarget(target: PsiElement): Boolean = target is PsiFileSystemItem

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<out PsiReference> {
      val expr = element as? PyStringLiteralExpression ?: return emptyArray()
      return PySoftFileReferenceSet(expr, this).allReferences
    }
  }

  /**
   * A soft file reference in Python string literals.
   *
   * * It's used only for code completion and ignored during code inspections
   * * It understands `~` as an alias for the user home path
   */
  open class PySoftFileReferenceSet(element: PyStringLiteralExpression, provider: PsiReferenceProvider) :
    PyStringLiteralFileReferenceSet(element.stringValue, element,
                                    ElementManipulators.getValueTextRange(element).startOffset,
                                    provider, !SystemInfo.isWindows, false, null) {

    override fun isSoft(): Boolean = true

    override fun createFileReference(range: TextRange, index: Int, text: String): FileReference? =
      doCreateFileReference(range, index, expandUserHome(text))

    open fun doCreateFileReference(range: TextRange,
                                   index: Int,
                                   expandedText: String?): FileReference? =
      super.createFileReference(range, index, expandedText)

    override fun isAbsolutePathReference(): Boolean =
      super.isAbsolutePathReference() || pathString.startsWith("~")

    private fun expandUserHome(text: String): String =
      when (text) {
        "~" -> SystemProperties.getUserHome()
        else -> text
      }
  }
}
