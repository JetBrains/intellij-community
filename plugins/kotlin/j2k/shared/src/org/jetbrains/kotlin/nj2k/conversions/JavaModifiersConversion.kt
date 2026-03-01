// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.config.ApiVersion.Companion.KOTLIN_1_9
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveConversion
import org.jetbrains.kotlin.nj2k.jvmAnnotation
import org.jetbrains.kotlin.nj2k.moduleApiVersion
import org.jetbrains.kotlin.nj2k.tree.JKAnnotation
import org.jetbrains.kotlin.nj2k.tree.JKAnnotationListOwner
import org.jetbrains.kotlin.nj2k.tree.JKOtherModifiersOwner
import org.jetbrains.kotlin.nj2k.tree.JKTreeElement
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.EXTERNAL
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.NATIVE
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.STRICTFP
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.SYNCHRONIZED
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.TRANSIENT
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.VOLATILE
import org.jetbrains.kotlin.nj2k.tree.elementByModifier
import org.jetbrains.kotlin.nj2k.tree.withFormattingFrom

/**
 * Converts Java-specific modifiers (for example, "volatile") to a Kotlin equivalent (usually an annotation).
 */
class JavaModifiersConversion(context: ConverterContext) : RecursiveConversion(context) {
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