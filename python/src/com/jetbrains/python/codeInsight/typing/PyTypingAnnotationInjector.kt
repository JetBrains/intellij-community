// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typing

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parents
import com.jetbrains.python.codeInsight.PyInjectionUtil
import com.jetbrains.python.codeInsight.PyInjectorBase
import com.jetbrains.python.codeInsight.functionTypeComments.PyFunctionTypeAnnotationDialect
import com.jetbrains.python.codeInsight.typeHints.PyTypeHintDialect
import com.jetbrains.python.psi.PyAnnotation
import com.jetbrains.python.psi.PyArgumentList
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyKeywordArgument
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PySubscriptionExpression
import com.jetbrains.python.psi.PyTupleExpression
import com.jetbrains.python.psi.PyTypeParameter
import com.jetbrains.python.psi.impl.stubs.PyTypingAliasStubType
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Injects fragments for type annotations either in string literals (quoted annotations containing forward references) or
 * in type comments starting with <tt># type:</tt>.
 *
 */
class PyTypingAnnotationInjector : PyInjectorBase() {
  override fun registerInjection(registrar: MultiHostRegistrar, context: PsiElement): PyInjectionUtil.InjectionResult? {
    // Handles only string literals containing quoted types
    getInjectedLanguage(context)?.let { language ->
      val element = PyInjectionUtil.getLargestStringLiteral(context)
      if (element != null) {
        return if (language === PyTypeHintDialect.INSTANCE && "\n" in element.text) {
          PyInjectionUtil.registerStringLiteralInjectionWithParenthesis(element, registrar, language)
        }
        else {
          PyInjectionUtil.registerStringLiteralInjection(element, registrar, language)
        }
      }
    }

    if (context is PsiComment &&
        context is PsiLanguageInjectionHost &&
        context.containingFile is PyFile
    ) {
      return registerCommentInjection(registrar, context)
    }
    return PyInjectionUtil.InjectionResult.EMPTY
  }

  override fun getInjectedLanguage(context: PsiElement): Language? {
    if (context !is PyStringLiteralExpression) return null

    // Walk up the PSI tree to find the innermost decisive type boundary.
    // Inner boundaries (e.g. call expression) shadow outer ones.
    when (context.classifyAnnotationPosition()) {
      TypeAnnotationPosition.TypeForm -> return PyTypeHintDialect.INSTANCE
      TypeAnnotationPosition.NonTypeForm -> return null
      null -> { /* no boundary found; fall through to secondary rules */ }
    }

    // bare annotation context — x: "T", def foo(x: Optional["T"]) -> "T": ...
    if (PsiTreeUtil.getParentOfType(context, PyAnnotation::class.java, true, PyCallExpression::class.java) != null &&
        context.stringValue.isTypingAnnotation()
    ) {
      return PyTypeHintDialect.INSTANCE
    }
    if (context.isInsideValueOfExplicitTypeAnnotation()) {
      return PyTypeHintDialect.INSTANCE
    }

    return null
  }
}

/**
 * Whether a string literal is in a TypeForm (type annotation) position or a NonTypeForm position.
 * Null means no decisive ancestor was found.
 */
private sealed interface TypeAnnotationPosition {
  data object TypeForm : TypeAnnotationPosition
  data object NonTypeForm : TypeAnnotationPosition
}

/**
 * Walks up the PSI tree from [this@classifyAnnotationPosition] and returns the innermost explicit TypeForm/NonTypeForm
 * boundary decision, or null if no decisive boundary is found.
 *
 * Decisive boundaries are constructs that explicitly route their sub-expressions into type or
 * non-type positions: `Annotated`, `Literal`, `cast`, `TypeVar`, `NewType`, `assert_type`,
 * class base lists, and PEP 695 `PyTypeParameter` bounds.
 *
 * All other expressions (generic subscriptions like `List[...]`, attribute access, etc.) are
 * transparent: they pass through the context from their parent.
 */
private fun PyStringLiteralExpression.classifyAnnotationPosition(): TypeAnnotationPosition? =
  parents(false).firstNotNullOfOrNull {
    this.classifyFromAncestor(it)
  }

/**
 * Returns the [TypeAnnotationPosition] that [ancestor] establishes for [this@classifyFromAncestor], or null if
 * [ancestor] is transparent and does not route type vs. non-type positions.
 */
private fun PsiElement.classifyFromAncestor(ancestor: PsiElement): TypeAnnotationPosition? = when (ancestor) {
  is PySubscriptionExpression -> this.classifySubscriptionPosition(ancestor)
  // Class bases are type positions: `class A(list["int"]): ...`
  // But strings nested inside non-type-routing calls are not: `class A(namedtuple('C', 'x y')): ...`
  is PyArgumentList -> when {
    ancestor.parent !is PyClass -> null
    this.parents(false).takeWhile { it !== ancestor }.any { it is PyKeywordArgument || it is PyCallExpression } ->
      TypeAnnotationPosition.NonTypeForm
    else -> TypeAnnotationPosition.TypeForm
  }
  // PEP 695 new-style TypeVar bound: `type X[T: "bound"] = ...`, `def foo[T: "bound"](): ...`
  is PyTypeParameter -> TypeAnnotationPosition.TypeForm
  is PyCallExpression -> this.classifyCallSitePosition(ancestor)
  else -> null
}

private fun PsiElement.classifySubscriptionPosition(subscription: PySubscriptionExpression): TypeAnnotationPosition? {
  val qualifier = subscription.qualifier as? PyReferenceExpression ?: return null
  val resolved = qualifier.resolveLocally()
  return when {
    // Annotated[T, meta...]: T is TypeForm, all metadata args are NonTypeForm
    resolved.any { it == PyTypingTypeProvider.ANNOTATED || it == PyTypingTypeProvider.ANNOTATED_EXT } -> {
      val index = subscription.indexExpression ?: return TypeAnnotationPosition.NonTypeForm
      val tuple = index as? PyTupleExpression
        ?: return TypeAnnotationPosition.TypeForm // single arg — it must be the type arg
      val firstArg = tuple.elements.firstOrNull() ?: return TypeAnnotationPosition.NonTypeForm
      if (PsiTreeUtil.isAncestor(firstArg, this, false)) TypeAnnotationPosition.TypeForm
      else TypeAnnotationPosition.NonTypeForm
    }
    // Literal[v, ...]: no argument is a type
    resolved.any { it == PyTypingTypeProvider.LITERAL || it == PyTypingTypeProvider.LITERAL_EXT } ->
      TypeAnnotationPosition.NonTypeForm
    // Other subscriptions (List[...], Optional[...], Union[...], etc.) are transparent
    else -> null
  }
}

private fun PsiElement.classifyCallSitePosition(call: PyCallExpression): TypeAnnotationPosition? {
  val callee = call.callee as? PyReferenceExpression ?: return null
  val resolved = callee.resolveLocally()
  val argList = call.argumentList ?: return null

  return when {
    // cast(T, v) — first positional arg (or `typ=`) is TypeForm
    resolved.any { it == PyTypingTypeProvider.CAST || it == PyTypingTypeProvider.CAST_EXT } -> {
      val typeArg = argList.getKeywordArgument("typ") ?: argList.arguments.firstOrNull()
      if (typeArg != null && PsiTreeUtil.isAncestor(typeArg, this, false))
        TypeAnnotationPosition.TypeForm
      else
        TypeAnnotationPosition.NonTypeForm
    }

    // TypeVar(name, *constraints, bound=T, default=T) — name is NonTypeForm, rest are TypeForm
    resolved.any { it == PyTypingTypeProvider.TYPE_VAR || it == PyTypingTypeProvider.TYPE_VAR_EXT } -> {
      val args = argList.arguments
      // First positional arg is the TypeVar name — not a type
      if (args.isNotEmpty() && args[0] !is PyKeywordArgument &&
          PsiTreeUtil.isAncestor(args[0], this, false)) {
        return TypeAnnotationPosition.NonTypeForm
      }
      // Remaining positional args are constraints
      for (arg in args.drop(1)) {
        if (arg is PyKeywordArgument) break
        if (PsiTreeUtil.isAncestor(arg, this, false)) return TypeAnnotationPosition.TypeForm
      }
      // Keyword args `bound` and `default` (PEP 696) are types
      for (kw in listOf("bound", "default")) {
        val value = argList.getKeywordArgument(kw)?.valueExpression
        if (value != null && PsiTreeUtil.isAncestor(value, this, false)) return TypeAnnotationPosition.TypeForm
      }
      TypeAnnotationPosition.NonTypeForm
    }

    // NewType(name, T) — second positional arg is TypeForm
    resolved.any { it == PyTypingTypeProvider.NEW_TYPE } -> {
      val args = argList.arguments
      if (args.size >= 2 && PsiTreeUtil.isAncestor(args[1], this, false))
        TypeAnnotationPosition.TypeForm
      else
        TypeAnnotationPosition.NonTypeForm
    }

    // assert_type(v, T) — second positional arg is TypeForm
    resolved.any { it == PyTypingTypeProvider.ASSERT_TYPE } -> {
      val args = argList.arguments
      if (args.size >= 2 && PsiTreeUtil.isAncestor(args[1], this, false))
        TypeAnnotationPosition.TypeForm
      else
        TypeAnnotationPosition.NonTypeForm
    }

    // For unknown calls, strings nested inside generic subscriptions are type arguments:
    // `x: call(set['str'])` — 'str' is the type arg of set[...] and should inject.
    // But only when the call itself is in an annotation context — otherwise something like
    // `print(A["foo"])` would incorrectly inject.
    else -> if (this.parents(false).takeWhile { it !== call }.any { it is PySubscriptionExpression } &&
               PsiTreeUtil.getParentOfType(call, PyAnnotation::class.java) != null)
      TypeAnnotationPosition.TypeForm
    else null
  }
}

private fun PyStringLiteralExpression.isInsideValueOfExplicitTypeAnnotation(): Boolean {
  val assignment = PsiTreeUtil.getParentOfType(this, PyAssignmentStatement::class.java)
  if (assignment == null || !PsiTreeUtil.isAncestor(assignment.assignedValue, this, false)) {
    return false
  }
  return PyTypingTypeProvider.isExplicitTypeAlias(assignment, TypeEvalContext.codeAnalysis(project, this.containingFile))
}

private fun registerCommentInjection(
  registrar: MultiHostRegistrar,
  host: PsiLanguageInjectionHost,
): PyInjectionUtil.InjectionResult {
  val text = host.text
  val annotationText = PyTypingTypeProvider.getTypeCommentValue(text)
  if (annotationText != null) {
    val language: Language?
    if (PyTypingTypeProvider.TYPE_IGNORE_PATTERN.matcher(text).matches()) {
      language = null
    }
    else if (host.isFunctionTypeComment()) {
      language = PyFunctionTypeAnnotationDialect.INSTANCE
    }
    else {
      language = PyTypeHintDialect.INSTANCE
    }
    if (language != null) {
      registrar.startInjecting(language)
      registrar.addPlace("", "", host, PyTypingTypeProvider.getTypeCommentValueRange(text)!!)
      registrar.doneInjecting()
      return PyInjectionUtil.InjectionResult(true, true)
    }
  }
  return PyInjectionUtil.InjectionResult.EMPTY
}

private fun PyReferenceExpression.resolveLocally(): List<String> =
  PyResolveUtil.resolveImportedElementQNameLocally(this).map { it.toString() }

private fun PsiElement.isFunctionTypeComment(): Boolean {
  val function = PsiTreeUtil.getParentOfType(this, PyFunction::class.java)
  return function != null && function.typeComment === this
}

private fun String.isTypingAnnotation(): Boolean =
  PyTypingAliasStubType.RE_TYPE_HINT_LIKE_STRING.toRegex() matches this
