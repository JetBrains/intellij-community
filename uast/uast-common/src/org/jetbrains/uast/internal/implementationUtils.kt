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
package org.jetbrains.uast.internal

import com.intellij.diagnostic.AttachmentFactory
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.visitor.UastVisitor

fun List<UElement>.acceptList(visitor: UastVisitor) {
  for (element in this) {
    element.accept(visitor)
  }
}

@Suppress("unused")
inline fun <reified T : UElement> T.log(text: String = ""): String {
  val className = T::class.java.simpleName
  return if (text.isEmpty()) className else "$className ($text)"
}

fun <U : UElement> Array<out Class<out UElement>>.accommodate(vararg makers: UElementAlternative<out U>): Sequence<U> {
  val makersSeq = makers.asSequence()
  return this.asSequence()
    .flatMap { requiredType -> makersSeq.filter { requiredType.isAssignableFrom(it.uType) } }
    .distinct()
    .mapNotNull { it.make.invoke() }
}

inline fun <reified U : UElement> alternative(noinline make: () -> U?) = UElementAlternative(U::class.java, make)

class UElementAlternative<U : UElement>(val uType: Class<U>, val make: () -> U?)

inline fun <reified T : UElement> convertOrReport(psiElement: PsiElement, parent: UElement): T? =
  convertOrReport(psiElement, parent, T::class.java)

fun <T : UElement> convertOrReport(psiElement: PsiElement, parent: UElement, expectedType: Class<T>): T? {

  fun UElement.safeToString(): String = RecursionManager.doPreventingRecursion(this, false) {
    toString()
  } ?: "<recursive `toString()` computation $javaClass>"

  fun mkAttachments(): Array<Attachment> = ArrayList<Attachment>().also { result ->
    result.add(Attachment("info.txt", buildString {
      appendLine("context: ${parent.javaClass}")
      appendLine("psiElement: ${psiElement.javaClass}")
      appendLine("expectedType: $expectedType")
    }))
    result.add(Attachment("psiElementContent.txt", runCatching { psiElement.text ?: "<null>" }.getOrElse { it.stackTraceToString() }))
    result.add(Attachment("uast-plugins.list", UastFacade.languagePlugins.joinToString("\n") { it.javaClass.toString() }))
    result.add(runCatching { psiElement.containingFile }
                 .mapCatching { it.virtualFile }
                 .fold({ AttachmentFactory.createAttachment(it) }, { Attachment("containingFile-exception.txt", it.stackTraceToString()) }))
  }.toTypedArray()

  val plugin = parent.sourcePsi?.let { UastFacade.findPlugin(it) } ?: UastFacade.findPlugin(psiElement)
  if (plugin == null) {
    Logger.getInstance(parent.javaClass)
      .error("cant get UAST plugin for ${parent.safeToString()} to convert element $psiElement", *mkAttachments())
    return null
  }
  val result = expectedType.cast(plugin.convertElement(psiElement, parent, expectedType))
  if (result == null) {
    Logger.getInstance(parent.javaClass)
      .error("failed to convert element $psiElement in ${parent.safeToString()}", *mkAttachments())
  }
  return result
}