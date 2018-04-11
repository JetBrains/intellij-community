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

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiParameter
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

fun UElement?.getUCallExpression(): UCallExpression? = this?.withContainingElements?.mapNotNull {
  when (it) {
    is UCallExpression -> it
    is UQualifiedReferenceExpression -> it.selector as? UCallExpression
    else -> null
  }
}?.firstOrNull()

@Deprecated(message = "This function is deprecated, use getContainingUFile", replaceWith = ReplaceWith("getContainingUFile()"))
fun UElement.getContainingFile() = getContainingUFile()

fun UElement.getContainingUFile() = getParentOfType<UFile>(UFile::class.java)

fun UElement.getContainingUClass() = getParentOfType<UClass>(UClass::class.java)
fun UElement.getContainingUMethod() = getParentOfType<UMethod>(UMethod::class.java)
fun UElement.getContainingUVariable() = getParentOfType<UVariable>(UVariable::class.java)

fun UElement.getContainingMethod() = getContainingUMethod()?.psi
fun UElement.getContainingClass() = getContainingUClass()?.psi
fun UElement.getContainingVariable() = getContainingUVariable()?.psi

fun PsiElement?.getContainingClass() = this?.let { PsiTreeUtil.getParentOfType(it, PsiClass::class.java) }

fun UElement.isChildOf(probablyParent: UElement?, strict: Boolean = false): Boolean {
  tailrec fun isChildOf(current: UElement?, probablyParent: UElement): Boolean {
    return when (current) {
      null -> false
      probablyParent -> true
      else -> isChildOf(current.uastParent, probablyParent)
    }
  }

  if (probablyParent == null) return false
  return isChildOf(if (strict) this else uastParent, probablyParent)
}

/**
 * Resolves the receiver element if it implements [UResolvable].
 *
 * @return the resolved element, or null if the element was not resolved, or if the receiver element is not an [UResolvable].
 */
fun UElement.tryResolve(): PsiElement? = (this as? UResolvable)?.resolve()

fun UElement.tryResolveNamed(): PsiNamedElement? = (this as? UResolvable)?.resolve() as? PsiNamedElement

fun UElement.tryResolveUDeclaration(context: UastContext): UDeclaration? {
  return (this as? UResolvable)?.resolve()?.let { context.convertElementWithParent(it, null) as? UDeclaration }
}

fun UReferenceExpression?.getQualifiedName() = (this?.resolve() as? PsiClass)?.qualifiedName

/**
 * Returns the String expression value, or null if the value can't be calculated or if the calculated value is not a String.
 */
fun UExpression.evaluateString(): String? = evaluate() as? String

/**
 * Get a physical [File] for this file, or null if there is no such file on disk.
 */
fun UFile.getIoFile(): File? = psi.virtualFile?.let { VfsUtilCore.virtualToIoFile(it) }

tailrec fun UElement.getUastContext(): UastContext {
  val psi = this.psi
  if (psi != null) {
    return ServiceManager.getService(psi.project, UastContext::class.java) ?: error("UastContext not found")
  }

  return (uastParent ?: error("PsiElement should exist at least for UFile")).getUastContext()
}

tailrec fun UElement.getLanguagePlugin(): UastLanguagePlugin {
  val psi = this.psi
  if (psi != null) {
    val uastContext = ServiceManager.getService(psi.project, UastContext::class.java) ?: error("UastContext not found")
    return uastContext.findPlugin(psi) ?: error("Language plugin was not found for $this (${this.javaClass.name})")
  }

  return (uastParent ?: error("PsiElement should exist at least for UFile")).getLanguagePlugin()
}

fun Collection<UElement?>.toPsiElements() = mapNotNull { it?.psi }

/**
 * A helper function for getting parents for given [PsiElement] that could be considered as identifier.
 * Useful for working with gutter accorting to recommendations in [com.intellij.codeInsight.daemon.LineMarkerProvider].
 */
@ApiStatus.Experimental
fun getUParentForIdentifier(identifier: PsiElement): UElement? {
  val uIdentifier = identifier.toUElementOfType<UIdentifier>() ?: return null
  return uIdentifier.uastParent
         ?: identifier.parent.toUElement() // a workaround for Kotlin < 1.2.30 which identifiers cant get parents

}

/**
 * @param arg expression in call arguments list of [this]
 * @return parameter that corresponds to the [arg] in declaration to which [this] resolves
 */
fun UCallExpression.getParameterForArgument(arg: UExpression): PsiParameter? {
  val psiMethod = resolve() ?: return null
  val parameters = psiMethod.parameterList.parameters

  if (this is UCallExpressionEx)
    return parameters.withIndex().find { (i, p) ->
      val argumentForParameter = getArgumentForParameter(i) ?: return@find false
      if (argumentForParameter == arg) return@find true
      if (p.isVarArgs && argumentForParameter is UExpressionList) return@find argumentForParameter.expressions.contains(arg)
      return@find false
    }?.value

  // not everyone implements UCallExpressionEx, lets try to guess
  val indexInArguments = valueArguments.indexOf(arg)
  if (parameters.size == valueArguments.count()) {
    return parameters.getOrNull(indexInArguments)
  }
  // probably it is a kotlin extension method
  if (parameters.size - 1 == valueArguments.count()) {
    val parameter = parameters.firstOrNull() ?: return null
    val receiverType = receiverType ?: return null
    if (!parameter.type.isAssignableFrom(receiverType)) return null
    if (!parameters.drop(1).zip(valueArguments)
        .all { (param, arg) -> arg.getExpressionType()?.let { param.type.isAssignableFrom(it) } == true }) return null
    return parameters.getOrNull(indexInArguments + 1)
  }
  //named parameters are not processed
  return null
}
