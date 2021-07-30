// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.common.kotlin

import com.intellij.psi.PsiElement
import com.intellij.util.PairProcessor
import com.intellij.util.ref.DebugReflectionUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.cli.jvm.compiler.CliTraceHolder
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.UastVisitor
import kotlin.test.fail

internal fun UFile.findFacade(): UClass? {
    return classes.find { it.psi is KtLightClassForFacade }
}

fun <T> UElement.findElementByText(refText: String, cls: Class<T>): T {
    val matchingElements = mutableListOf<T>()
    accept(object : UastVisitor {
        override fun visitElement(node: UElement): Boolean {
            if (cls.isInstance(node) && node.psi?.text == refText) {
                matchingElements.add(node as T)
            }
            return false
        }
    })

    if (matchingElements.isEmpty()) {
        throw IllegalArgumentException("Reference '$refText' not found")
    }
    if (matchingElements.size != 1) {
        throw IllegalArgumentException("Reference '$refText' is ambiguous")
    }
    return matchingElements.single()
}

inline fun <reified T : Any> UElement.findElementByText(refText: String): T = findElementByText(refText, T::class.java)

inline fun <reified T : UElement> UElement.findElementByTextFromPsi(refText: String, strict: Boolean = false): T =
    (this.psi ?: fail("no psi for $this")).findUElementByTextFromPsi(refText, strict)

inline fun <reified T : UElement> PsiElement.findUElementByTextFromPsi(refText: String, strict: Boolean = false): T {
    val elementAtStart = this.findElementAt(this.text.indexOf(refText))
        ?: throw AssertionError("requested text '$refText' was not found in $this")

    val targetElements = elementAtStart.parentsWithSelf
    var uElementContainingText: T? = null

    for (targetElement in targetElements) {
        if (strict && !targetElement.text.contains(refText)) {
            continue
        }

        val foundElement = targetElement.toUElementOfType<T>()
        if (foundElement != null) {
            uElementContainingText = foundElement
            break
        }
    }

    if (uElementContainingText == null) {
        throw AssertionError("requested text '$refText' not found as '${T::class.java.canonicalName}' in $this")
    }

    if (strict && uElementContainingText.psi != null && uElementContainingText.psi?.text != refText) {
        throw AssertionError("requested text '$refText' found as '${uElementContainingText.psi?.text}' in $uElementContainingText")
    }
    return uElementContainingText
}

private val descriptorsClasses = listOf(AnnotationDescriptor::class, DeclarationDescriptor::class)

fun checkDescriptorsLeak(node: UElement) {
    DebugReflectionUtil.walkObjects(
        10,
        mapOf(node to node.javaClass.name),
        Any::class.java,
        { it !is CliTraceHolder },
        PairProcessor { value, backLink ->
            descriptorsClasses.find { it.isInstance(value) }?.let {
                TestCase.fail("""Leaked descriptor ${it.qualifiedName} in ${node.javaClass.name}\n$backLink""")
                false
            } ?: true
        })
}

fun <T> T?.orFail(msg: String): T {
    return this ?: throw AssertionError(msg)
}
