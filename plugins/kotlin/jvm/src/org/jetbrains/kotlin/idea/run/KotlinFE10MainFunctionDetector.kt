package org.jetbrains.kotlin.idea.run

import org.jetbrains.kotlin.idea.MainFunctionDetector
import org.jetbrains.kotlin.idea.base.lineMarkers.run.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

internal class KotlinFE10MainFunctionDetector : KotlinMainFunctionDetector {
    override fun isMain(function: KtNamedFunction): Boolean {
        return hasMain(listOf(function))
    }

    override fun hasMain(declarations: List<KtDeclaration>): Boolean {
        if (declarations.isEmpty()) return false

        val languageVersionSettings = declarations.first().languageVersionSettings
        val mainFunctionDetector = MainFunctionDetector(languageVersionSettings) { it.resolveToDescriptorIfAny() }
        return declarations.any { it is KtNamedFunction && mainFunctionDetector.isMain(it) }
    }
}