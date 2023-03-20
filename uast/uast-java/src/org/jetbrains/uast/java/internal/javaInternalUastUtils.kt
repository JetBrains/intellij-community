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
package org.jetbrains.uast.java

import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastBinaryOperator

internal fun IElementType.getOperatorType() = when (this) {
  JavaTokenType.EQ -> UastBinaryOperator.ASSIGN
  JavaTokenType.PLUS -> UastBinaryOperator.PLUS
  JavaTokenType.MINUS -> UastBinaryOperator.MINUS
  JavaTokenType.ASTERISK -> UastBinaryOperator.MULTIPLY
  JavaTokenType.DIV -> UastBinaryOperator.DIV
  JavaTokenType.PERC -> UastBinaryOperator.MOD
  JavaTokenType.ANDAND -> UastBinaryOperator.LOGICAL_AND
  JavaTokenType.OROR -> UastBinaryOperator.LOGICAL_OR
  JavaTokenType.OR -> UastBinaryOperator.BITWISE_OR
  JavaTokenType.AND -> UastBinaryOperator.BITWISE_AND
  JavaTokenType.XOR -> UastBinaryOperator.BITWISE_XOR
  JavaTokenType.EQEQ -> UastBinaryOperator.IDENTITY_EQUALS
  JavaTokenType.NE -> UastBinaryOperator.IDENTITY_NOT_EQUALS
  JavaTokenType.GT -> UastBinaryOperator.GREATER
  JavaTokenType.GE -> UastBinaryOperator.GREATER_OR_EQUALS
  JavaTokenType.LT -> UastBinaryOperator.LESS
  JavaTokenType.LE -> UastBinaryOperator.LESS_OR_EQUALS
  JavaTokenType.LTLT -> UastBinaryOperator.SHIFT_LEFT
  JavaTokenType.GTGT -> UastBinaryOperator.SHIFT_RIGHT
  JavaTokenType.GTGTGT -> UastBinaryOperator.UNSIGNED_SHIFT_RIGHT
  JavaTokenType.PLUSEQ -> UastBinaryOperator.PLUS_ASSIGN
  JavaTokenType.MINUSEQ -> UastBinaryOperator.MINUS_ASSIGN
  JavaTokenType.ASTERISKEQ -> UastBinaryOperator.MULTIPLY_ASSIGN
  JavaTokenType.DIVEQ -> UastBinaryOperator.DIVIDE_ASSIGN
  JavaTokenType.PERCEQ -> UastBinaryOperator.REMAINDER_ASSIGN
  JavaTokenType.ANDEQ -> UastBinaryOperator.AND_ASSIGN
  JavaTokenType.XOREQ -> UastBinaryOperator.XOR_ASSIGN
  JavaTokenType.OREQ -> UastBinaryOperator.OR_ASSIGN
  JavaTokenType.LTLTEQ -> UastBinaryOperator.SHIFT_LEFT_ASSIGN
  JavaTokenType.GTGTEQ -> UastBinaryOperator.SHIFT_RIGHT_ASSIGN
  JavaTokenType.GTGTGTEQ -> UastBinaryOperator.UNSIGNED_SHIFT_RIGHT_ASSIGN
  else -> UastBinaryOperator.OTHER
}

internal fun <T> singletonListOrEmpty(element: T?) = if (element != null) listOf(element) else emptyList<T>()

@Suppress("NOTHING_TO_INLINE")
internal inline fun String?.orAnonymous(kind: String = ""): String {
  return this ?: ("<anonymous" + (if (kind.isNotBlank()) " $kind" else "") + ">")
}

val PsiModifierListOwner.annotations: Array<PsiAnnotation>
  get() = modifierList?.annotations ?: emptyArray()

internal inline fun <reified T : UDeclaration, reified P : PsiElement> unwrap(element: P): P {
  val unwrapped = if (element is T) element.javaPsi else element
  assert(unwrapped !is UElement)
  return unwrapped as P
}

internal fun PsiElement.getChildByRole(role: Int) = (this as? CompositeElement)?.findChildByRoleAsPsiElement(role)

/** Returns true if the given element is written in Java. */
fun isJava(element: PsiElement?): Boolean {
  return element != null && isJava(element.language)
}

/** Returns true if the given language is Java. */
fun isJava(language: Language?): Boolean {
  return language == JavaLanguage.INSTANCE
}

internal fun <T> lazyPub(initializer: () -> T): Lazy<T> = lazy(LazyThreadSafetyMode.PUBLICATION, initializer)

@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
internal inline fun <reified T : Any> Any?.asSafely(): @kotlin.internal.NoInfer T? = this as? T
