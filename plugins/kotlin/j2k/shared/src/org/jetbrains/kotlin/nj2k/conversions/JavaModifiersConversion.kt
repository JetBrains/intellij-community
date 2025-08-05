// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.config.ApiVersion.Companion.KOTLIN_1_9
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.jvmAnnotation
import org.jetbrains.kotlin.nj2k.moduleApiVersion
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.*

/**
 * Converts Java-specific modifiers (for example, "volatile") to a Kotlin equivalent (usually an annotation).
 */
class JavaModifiersConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element is JKOtherModifiersOwner && element is JKAnnotationListOwner) {
            element.elementByModifier(VOLATILE)?.let { modifierElement ->
                element.otherModifierElements -= modifierElement
                val annotationFqName = if (moduleApiVersion >= KOTLIN_1_9) "kotlin.concurrent.Volatile" else "kotlin.jvm.Volatile"
                element.annotationList.annotations +=
                    JKAnnotation(symbolProvider.provideClassSymbol(annotationFqName)).withFormattingFrom(modifierElement)
            }

            element.elementByModifier(TRANSIENT)?.let { modifierElement ->
                element.otherModifierElements -= modifierElement
                element.annotationList.annotations +=
                    jvmAnnotation("Transient", symbolProvider).withFormattingFrom(modifierElement)
            }

            element.elementByModifier(STRICTFP)?.let { modifierElement ->
                element.otherModifierElements -= modifierElement
                element.annotationList.annotations +=
                    jvmAnnotation("Strictfp", symbolProvider).withFormattingFrom(modifierElement)
            }

            element.elementByModifier(SYNCHRONIZED)?.let { modifierElement ->
                element.otherModifierElements -= modifierElement
                element.annotationList.annotations +=
                    jvmAnnotation("Synchronized", symbolProvider).withFormattingFrom(modifierElement)
            }

            element.elementByModifier(NATIVE)?.let { modifierElement ->
                modifierElement.otherModifier = EXTERNAL
            }
        }

        return recurse(element)
    }
}