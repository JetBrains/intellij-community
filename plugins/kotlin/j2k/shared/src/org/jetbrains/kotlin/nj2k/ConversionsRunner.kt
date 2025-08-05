// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.J2kConverterExtension
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K1_NEW
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K2
import org.jetbrains.kotlin.nj2k.tree.JKTreeRoot

object ConversionsRunner {
    context(_: KaSession)
    fun doApply(
        trees: List<JKTreeRoot>,
        context: ConverterContext,
        updateProgress: (conversionIndex: Int, conversionCount: Int, fileIndex: Int, description: String) -> Unit
    ) {
        val j2kKind = if (KotlinPluginModeProvider.isK2Mode()) K2 else K1_NEW
        val conversions = J2kConverterExtension.extension(j2kKind).getConversions(context)
        val applyingConversionsMessage: String = KotlinNJ2KBundle.message("j2k.applying.conversions")

        for ((conversionIndex, conversion) in conversions.withIndex()) {
            if (context.settings.basicMode && !conversion.isEnabledInBasicMode()) {
                continue
            }

            val treeSequence = trees.asSequence().onEachIndexed { index, _ ->
                updateProgress(conversionIndex, conversions.size, index, applyingConversionsMessage)
            }

            try {
                conversion.runForEach(treeSequence, context)
            } catch (ignored: UninitializedPropertyAccessException) {
                // This should only happen on copy-pasting broken (incomplete) code
            }
        }
    }
}
