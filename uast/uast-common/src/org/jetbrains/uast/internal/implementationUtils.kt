// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.internal

import com.intellij.diagnostic.CoreAttachmentFactory
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.util.containers.addAllIfNotNull
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.visitor.UastVisitor

fun List<UElement>.acceptList(visitor: UastVisitor) {
  for (element in this) {
    element.accept(visitor)
  }
}

@Suppress("UnusedReceiverParameter")
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

fun <U : UElement> Class<out UElement>.accommodate(a1: UElementAlternative<out U>, a2: UElementAlternative<out U>): U? {
  return if (this.isAssignableFrom(a1.uType)) {
    a1.make.invoke()
  }
  else if (this.isAssignableFrom(a2.uType)) {
    a2.make.invoke()
  }
  else null
}

inline fun <reified U : UElement> alternative(noinline make: () -> U?): UElementAlternative<U> = UElementAlternative(U::class.java, make)

class UElementAlternative<U : UElement>(val uType: Class<U>, val make: () -> U?)

inline fun <reified T : UElement> convertOrReport(psiElement: PsiElement, parent: UElement): T? =
  convertOrReport(psiElement, parent, T::class.java)


private val isInsideReporting = ThreadLocal<Boolean>()

private val conversionLoggerCollector = ThreadLocalTroubleCollector()

val CONVERSION_LOGGER: ThreadLocalTroubleCollector.Logger = conversionLoggerCollector.logger

fun <T : UElement> convertOrReport(psiElement: PsiElement, parent: UElement, expectedType: Class<T>): T? {

  fun UElement.safeToString(): String = if (isInsideReporting.get() != true)
    isInsideReporting.withValue(true) {
      toString()
    }
  else "<recursive `safeToString()` computation $javaClass>"

  fun mkAttachments(vararg attachments: Attachment): Array<Attachment> = ArrayList<Attachment>().also { result ->
    result.addAllIfNotNull(*attachments)
    result.add(Attachment("info.txt", buildString {
      appendLine("context: ${parent.javaClass}")
      appendLine("psiElement: ${psiElement.javaClass}, valid = ${runCatching { psiElement.isValid }.getOrNull()}")
      appendLine("expectedType: $expectedType")
    }))
    result.add(Attachment("psiElementContent.txt", runCatching { psiElement.text ?: "<null>" }.getOrElse { it.stackTraceToString() }))
    result.add(Attachment("uast-plugins.list", UastFacade.languagePlugins.joinToString("\n") { it.javaClass.toString() }))
    result.add(runCatching { psiElement.containingFile }
                 .mapCatching { it.virtualFile }
                 .fold({ CoreAttachmentFactory.createAttachment(it) }, { Attachment("containingFile-exception.txt", it.stackTraceToString()) }))
  }.toTypedArray()

  val plugin = parent.sourcePsi?.let { UastFacade.findPlugin(it) } ?: UastFacade.findPlugin(psiElement)
  if (plugin == null) {
    if (isInsideReporting.get() != true)
      Logger.getInstance(parent.javaClass)
        .error("cant get UAST plugin for ${parent.safeToString()} to convert element $psiElement", *mkAttachments())
    return null
  }
  val (result, log) = conversionLoggerCollector.withCollectingInfo {
    expectedType.cast(plugin.convertElement(psiElement, parent, expectedType))
  }
  if (result == null && isInsideReporting.get() != true) {
    Logger.getInstance(parent.javaClass)
      .error(
        "failed to convert element $psiElement (${psiElement.javaClass}) in ${parent.safeToString()}, plugin = $plugin",
        *mkAttachments(Attachment("conversion-log.txt", log)))
  }
  return result
}

internal inline fun <T, R> ThreadLocal<T>.withValue(value: T, block: () -> R): R {
  val old = this.get()
  if (old == value) return block.invoke()
  try {
    this.set(value)
    return block.invoke()
  }
  finally {
    if (old == null)
      this.remove()
    else
      this.set(old)
  }
}