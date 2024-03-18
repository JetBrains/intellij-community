// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("UastUtils")

package org.jetbrains.uast

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.SmartList
import one.util.streamex.StreamEx
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.io.File
import java.util.stream.Stream

inline fun <reified T : UElement> UElement.getParentOfType(strict: Boolean = true): T? = getParentOfType(T::class.java, strict)

@JvmOverloads
fun <T : UElement> UElement.getParentOfType(parentClass: Class<out T>, strict: Boolean = true): T? {
  var element = (if (strict) uastParent else this) ?: return null
  while (true) {
    if (parentClass.isInstance(element)) {
      @Suppress("UNCHECKED_CAST")
      return element as T
    }
    element = element.uastParent ?: return null
  }
}

fun UElement.skipParentOfType(strict: Boolean, vararg parentClasses: Class<out UElement>): UElement? {
  var element = (if (strict) uastParent else this) ?: return null
  while (true) {
    if (!parentClasses.any { it.isInstance(element) }) {
      return element
    }
    element = element.uastParent ?: return null
  }
}

@SafeVarargs
fun <T : UElement> UElement.getParentOfType(
  parentClass: Class<out T>,
  strict: Boolean = true,
  vararg terminators: Class<out UElement>
): T? {
  var element = (if (strict) uastParent else this) ?: return null
  while (true) {
    if (parentClass.isInstance(element)) {
      @Suppress("UNCHECKED_CAST")
      return element as T
    }
    if (terminators.any { it.isInstance(element) }) {
      return null
    }
    element = element.uastParent ?: return null
  }
}

fun <T : UElement> UElement.getParentOfType(
  strict: Boolean = true,
  firstParentClass: Class<out T>,
  vararg parentClasses: Class<out T>
): T? {
  var element = (if (strict) uastParent else this) ?: return null
  while (true) {
    if (firstParentClass.isInstance(element)) {
      @Suppress("UNCHECKED_CAST")
      return element as T
    }
    if (parentClasses.any { it.isInstance(element) }) {
      @Suppress("UNCHECKED_CAST")
      return element as T
    }
    element = element.uastParent ?: return null
  }
}

@JvmOverloads
fun UElement?.getUCallExpression(searchLimit: Int = Int.MAX_VALUE): UCallExpression? {
  if (this == null) return null
  var u: UElement? = this
  for (i in 1..searchLimit) {
    if (u == null) break
    if (u is UCallExpression) return u

    if (u is UQualifiedReferenceExpression) {
      val selector = u.selector
      if (selector is UCallExpression) return selector
    }
    u = u.uastParent
  }
  return null
}

fun UElement.getContainingUFile(): UFile? = getParentOfType(UFile::class.java, false)

fun UElement.getContainingUClass(): UClass? = getParentOfType(UClass::class.java)
fun UElement.getContainingUMethod(): UMethod? = getParentOfType(UMethod::class.java)
fun UElement.getContainingUVariable(): UVariable? = getParentOfType(UVariable::class.java)

fun <T : UElement> PsiElement?.findContaining(clazz: Class<T>): T? {
  var element = this
  while (element != null && element !is PsiFileSystemItem) {
    element.toUElement(clazz)?.let { return it }
    element = element.parent
  }
  return null
}

fun <T : UElement> PsiElement?.findAnyContaining(vararg types: Class<out T>): T? = findAnyContaining(Int.Companion.MAX_VALUE, *types)

fun <T : UElement> PsiElement?.findAnyContaining(depthLimit: Int, vararg types: Class<out T>): T? {
  var element = this
  var i = 0
  while (i < depthLimit && element != null && element !is PsiFileSystemItem) {
    element.toUElementOfExpectedTypes(*types)?.let { return it }
    element = element.parent
    i++
  }
  return null
}

@JvmOverloads
fun isPsiAncestor(ancestor: UElement, child: UElement, strict: Boolean = false): Boolean {
  val ancestorPsi = ancestor.sourcePsi ?: return false
  val childPsi = child.sourcePsi ?: return false
  return PsiTreeUtil.isAncestor(ancestorPsi, childPsi, strict)
}

fun UElement.isUastChildOf(probablyParent: UElement?, strict: Boolean = false): Boolean {
  tailrec fun isChildOf(current: UElement?, probablyParent: UElement): Boolean {
    return when (current) {
      null -> false
      probablyParent -> true
      else -> isChildOf(current.uastParent, probablyParent)
    }
  }

  if (probablyParent == null) return false
  return isChildOf(if (strict) uastParent else this, probablyParent)
}

/**
 * Resolves the receiver element if it implements [UResolvable].
 *
 * @return the resolved element, or null if the element was not resolved, or if the receiver element is not an [UResolvable].
 */
fun UElement.tryResolve(): PsiElement? = (this as? UResolvable)?.resolve()

fun UElement.tryResolveNamed(): PsiNamedElement? = (this as? UResolvable)?.resolve() as? PsiNamedElement

fun UReferenceExpression?.getQualifiedName(): String? = (this?.resolve() as? PsiClass)?.qualifiedName

/**
 * Returns the String expression value, or null if the value can't be calculated, or if the calculated value is not a String or an integral literal.
 */
fun UExpression.evaluateString(): String? = evaluate().takeIf { it is String || isIntegralLiteral() }?.toString()

fun UExpression.skipParenthesizedExprDown(): UExpression {
  var expression = this
  while (expression is UParenthesizedExpression) {
    expression = expression.expression
  }
  return expression
}

fun skipParenthesizedExprUp(elem: UElement?): UElement? {
  var parent = elem
  while (parent is UParenthesizedExpression) {
    parent = parent.uastParent
  }
  return parent
}


/**
 * Get a physical [File] for this file, or null if there is no such file on disk.
 */
fun UFile.getIoFile(): File? = sourcePsi.virtualFile?.let { VfsUtilCore.virtualToIoFile(it) }

@Deprecated("use UastFacade", ReplaceWith("UastFacade"))
@ApiStatus.ScheduledForRemoval
@Suppress("DEPRECATION")
tailrec fun UElement.getUastContext(): UastContext {
  val psi = this.sourcePsi
  if (psi != null) {
    return psi.project.getService(UastContext::class.java) ?: error("UastContext not found")
  }

  return (uastParent ?: error("PsiElement should exist at least for UFile")).getUastContext()
}

@Deprecated("could unexpectedly throw exception", ReplaceWith("UastFacade.findPlugin"))
@ApiStatus.ScheduledForRemoval
tailrec fun UElement.getLanguagePlugin(): UastLanguagePlugin {
  val psi = this.sourcePsi
  if (psi != null) {
    return UastFacade.findPlugin(psi) ?: error("Language plugin was not found for $this (${this.javaClass.name})")
  }

  return (uastParent ?: error("PsiElement should exist at least for UFile")).getLanguagePlugin()
}

fun Collection<UElement?>.toPsiElements(): List<PsiElement> = mapNotNull { it?.sourcePsi }

/**
 * A helper function for getting parents for given [PsiElement] that could be considered as identifier.
 * Useful for working with gutter according to recommendations in [com.intellij.codeInsight.daemon.LineMarkerProvider].
 *
 * @see [getUParentForAnnotationIdentifier] for working with annotations
 */
fun getUParentForIdentifier(identifier: PsiElement): UElement? {
  val uIdentifier = identifier.toUElementOfType<UIdentifier>() ?: return null
  return uIdentifier.uastParent
}

/**
 * @see UCallExpression.getArgumentForParameter
 *
 * @param arg expression in call arguments list of [this]
 * @return parameter that corresponds to the [arg] in declaration to which [this] resolves
 */
fun UCallExpression.getParameterForArgument(arg: UExpression): PsiParameter? {
  val psiMethod = resolve() ?: return null
  val parameters = psiMethod.parameterList.parameters

  return parameters.withIndex().find { (i, p) ->
    val argumentForParameter = getArgumentForParameter(i) ?: return@find false
    if (argumentForParameter == arg) return@find true
    if (p.isVarArgs && argumentForParameter is UExpressionList) return@find argumentForParameter.expressions.contains(arg)
    return@find false
  }?.value
}

@ApiStatus.Experimental
tailrec fun UElement.isLastElementInControlFlow(scopeElement: UElement? = null): Boolean =
  when (val parent = this.uastParent) {
    scopeElement -> if (scopeElement is UBlockExpression) scopeElement.expressions.lastOrNull() == this else true
    is UBlockExpression -> if (parent.expressions.lastOrNull() == this) parent.isLastElementInControlFlow(scopeElement) else false
    is UElement -> parent.isLastElementInControlFlow(scopeElement)
    else -> false
  }

fun UNamedExpression.getAnnotationMethod(): PsiMethod? {
  val annotation: UAnnotation? = getParentOfType(UAnnotation::class.java, true)
  val fqn = annotation?.qualifiedName ?: return null
  val annotationSrc = annotation.sourcePsi
  if (annotationSrc == null) return null
  val psiClass = JavaPsiFacade.getInstance(annotationSrc.project).findClass(fqn, annotationSrc.resolveScope)
  if (psiClass != null && psiClass.isAnnotationType) {
    return psiClass.findMethodsByName(this.name ?: "value", false).firstOrNull()
  }
  return null
}

val UElement.textRange: TextRange?
  get() = sourcePsi?.textRange

/**
 * A helper function for getting [UMethod] for element for LineMarker.
 * It handles cases, when [getUParentForIdentifier] returns same `parent` for more than one `identifier`.
 * Such situations cause multiple LineMarkers for same declaration.
 */
inline fun <reified T : UDeclaration> getUParentForDeclarationLineMarkerElement(lineMarkerElement: PsiElement): T? {
  val parent = getUParentForIdentifier(lineMarkerElement) as? T ?: return null
  if (parent.uastAnchor.sourcePsiElement != lineMarkerElement) return null
  return parent
}

/**
 * Returns stream of sub-expressions of supplied expression which could be equal by reference to resulting
 * value of the expression. The expression value is guaranteed to be equal to one of returned sub-expressions.
 *
 * E.g. for `(if (flag) (b) else (c))` the stream will contain b and c.
 *
 * @param expression expression to create a stream from
 * @return a new stream
 */
fun nonStructuralChildren(expression: UExpression): Stream<UExpression> {
  return StreamEx.ofTree(expression) { e ->
    when (e) {
      is UBlockExpression -> StreamEx.ofNullable(e.expressions.lastOrNull())
      is UIfExpression -> StreamEx.of(e.thenExpression, e.elseExpression).nonNull()
      is UParenthesizedExpression -> StreamEx.ofNullable(e.expression)
      is USwitchExpression -> {
        val result: MutableList<UExpression> = SmartList()
        e.accept(object : AbstractUastVisitor() {
          override fun visitYieldExpression(node: UYieldExpression): Boolean {
            if (e == node.jumpTarget && node.expression !is UReturnExpression) {
              node.expression?.let { result.add(it) }
            }
            return true
          }
        })
        StreamEx.of(result)
      }
      else -> null
    }
  }.remove { e ->
    e is UBlockExpression || e is UIfExpression || e is UParenthesizedExpression || e is USwitchExpression
  }
}