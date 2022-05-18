package org.jetbrains.kotlin.idea.run

import org.jetbrains.kotlin.idea.MainFunctionDetector
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

internal class KotlinFE10MainFunctionLocatingService : KotlinMainFunctionLocatingService {
    override fun isMain(function: KtNamedFunction): Boolean =
        mainFunctionDetector(function).isMain(function)

    private fun mainFunctionDetector(function: KtDeclaration) =
        MainFunctionDetector(function.languageVersionSettings) {
            it.resolveToDescriptorIfAny()
        }

    override fun hasMain(declarations: List<KtDeclaration>): Boolean {
        val mainFunctionDetector = mainFunctionDetector(declarations.firstOrNull() ?: return false)

        return declarations.any { it is KtNamedFunction && mainFunctionDetector.isMain(it) }
    }
}