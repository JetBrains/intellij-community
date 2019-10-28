/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmMultifileClass
@file:JvmName("UastUtils")

package org.jetbrains.uast

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.ApiStatus
import java.io.File

inline fun <reified T : UElement> UElement.getParentOfType(strict: Boolean = true): T? = getParentOfType(T::class.java, strict)

@JvmOverloads
fun <T : UElement> UElement.getParentOfType(parentClass: Class<out UElement>, strict: Boolean = true): T? {
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
  var element = (if (strict) uastParent else this)  ?: return null
  while (true) {
    if (!parentClasses.any { it.isInstance(element) }) {
      return element
    }
    element = element.uastParent ?: return null
  }
}

fun <T : UElement> UElement.getParentOfType(
  parentClass: Class<out UElement>,
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
fun UElement?.getUCallExpression(searchLimit: Int = Int.MAX_VALUE): UCallExpression? =
  this?.withContainingElements?.take(searchLimit)?.mapNotNull {
    when (it) {
      is UCallExpression -> it
      is UQualifiedReferenceExpression -> it.selector as? UCallExpression
      else -> null
    }
  }?.firstOrNull()

@Deprecated(message = "This function is deprecated, use getContainingUFile, will be removed in IDEA 2019.3",
            replaceWith = ReplaceWith("getContainingUFile()"))
fun UElement.getContainingFile(): UFile? = getContainingUFile()

fun UElement.getContainingUFile(): UFile? = getParentOfType(UFile::class.java)

fun UElement.getContainingUClass(): UClass? = getParentOfType(UClass::class.java)
fun UElement.getContainingUMethod(): UMethod? = getParentOfType(UMethod::class.java)
fun UElement.getContainingUVariable(): UVariable? = getParentOfType(UVariable::class.java)

@Deprecated(message = "Useless function, will be removed in IDEA 2019.3", replaceWith = ReplaceWith("getContainingMethod()?.javaPsi"))
fun UElement.getContainingMethod(): PsiMethod? = getContainingUMethod()?.javaPsi

@Deprecated(message = "Useless function, will be removed in IDEA 2019.3", replaceWith = ReplaceWith("getContainingUClass()?.javaPsi"))
fun UElement.getContainingClass(): PsiClass? = getContainingUClass()?.javaPsi

@Deprecated(message = "Useless function, will be removed in IDEA 2019.3",
            replaceWith = ReplaceWith("PsiTreeUtil.getParentOfType(this, PsiClass::class.java)"))
fun PsiElement?.getContainingClass(): PsiClass? = this?.let { PsiTreeUtil.getParentOfType(it, PsiClass::class.java) }

fun <T : UElement> PsiElement?.findContaining(clazz: Class<T>): T? {
  var element = this
  while (element != null && element !is PsiFileSystemItem) {
    element.toUElement(clazz)?.let { return it }
    element = element.parent
  }
  return null
}

fun isPsiAncestor(ancestor: UElement, child: UElement): Boolean {
  val ancestorPsi = ancestor.sourcePsi ?: return false
  val childPsi = child.sourcePsi ?: return false
  return PsiTreeUtil.isAncestor(ancestorPsi, childPsi, false)
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

@Deprecated(message = "Useless function, will be removed in IDEA 2019.3",
            replaceWith = ReplaceWith("(this as? UResolvable)?.resolve().toUElementOfType<UDeclaration>()"))
@Suppress("Deprecation")
fun UElement.tryResolveUDeclaration(context: UastContext): UDeclaration? {
  return (this as? UResolvable)?.resolve()?.let { context.convertElementWithParent(it, null) as? UDeclaration }
}

fun UReferenceExpression?.getQualifiedName(): String? = (this?.resolve() as? PsiClass)?.qualifiedName

/**
 * Returns the String expression value, or null if the value can't be calculated or if the calculated value is not a String.
 */
fun UExpression.evaluateString(): String? = evaluate() as? String

/**
 * Get a physical [File] for this file, or null if there is no such file on disk.
 */
fun UFile.getIoFile(): File? = sourcePsi.virtualFile?.let { VfsUtilCore.virtualToIoFile(it) }

@Deprecated("use UastFacade", ReplaceWith("UastFacade"))
@Suppress("Deprecation")
tailrec fun UElement.getUastContext(): UastContext {
  val psi = this.sourcePsi
  if (psi != null) {
    return ServiceManager.getService(psi.project, UastContext::class.java) ?: error("UastContext not found")
  }

  return (uastParent ?: error("PsiElement should exist at least for UFile")).getUastContext()
}

@Deprecated("could unexpectedly throw exception", ReplaceWith("UastFacade.findPlugin"))
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
 * @param arg expression in call arguments list of [this]
 * @return parameter that corresponds to the [arg] in declaration to which [this] resolves
 */
fun UCallExpression.getParameterForArgument(arg: UExpression): PsiParameter? {
  val psiMethod = resolve() ?: return null
  val parameters = psiMethod.parameterList.parameters

  return parameters.withIndex().find { (i, p) ->
    val argumentForParameter = getArgumentForParameter(i) ?: return@find false
    if (wrapULiteral(argumentForParameter) == wrapULiteral(arg)) return@find true
    if (arg is ULambdaExpression && arg.sourcePsi?.parent == argumentForParameter.sourcePsi) return@find true // workaround for KT-25297
    if (p.isVarArgs && argumentForParameter is UExpressionList) return@find argumentForParameter.expressions.contains(arg)
    return@find false
  }?.value
}

@ApiStatus.Experimental
tailrec fun UElement.isLastElementInControlFlow(scopeElement: UElement? = null): Boolean =
  when (val parent = this.uastParent) {
    scopeElement -> true
    is UBlockExpression -> if (parent.expressions.lastOrNull() == this) parent.isLastElementInControlFlow(scopeElement) else false
    is UElement -> parent.isLastElementInControlFlow(scopeElement)
    else -> false
  }

