// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.J2kConverterExtension
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K1_NEW
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K2
import org.jetbrains.kotlin.nj2k.tree.JKTreeRoot

object ConversionsRunner {
    fun doApply(
        trees: List<JKTreeRoot>,
        context: ConverterContext,
    ) {
        val j2kKind = if (KotlinPluginModeProvider.isK2Mode()) K2 else K1_NEW
        val conversions = J2kConverterExtension.extension(j2kKind).getConversions(context)

        for (conversion in conversions) {
            if (context.settings.basicMode && !conversion.isEnabledInBasicMode()) continue

            try {
                conversion.runForEach(trees.asSequence(), context)
            } catch (ignored: UninitializedPropertyAccessException) {
                // This should only happen on copy-pasting broken (incomplete) code
            }
        }
    }
}
