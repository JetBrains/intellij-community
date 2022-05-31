package org.jetbrains.kotlin.idea.run

import org.jetbrains.kotlin.idea.MainFunctionDetector
import org.jetbrains.kotlin.idea.base.lineMarkers.run.KotlinMainFunctionLocatingService
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

internal class KotlinFE10MainFunctionLocatingService : KotlinMainFunctionLocatingService {
    override fun isMain(function: KtNamedFunction): Boolean = hasMain(listOf(function))

    override fun hasMain(declarations: List<KtDeclaration>): Boolean {
        if (declarations.isEmpty()) return false
        val mainFunctionDetector = mainFunctionDetector(declarations.first())

        return declarations.any { it is KtNamedFunction && mainFunctionDetector.isMain(it) }
    }

    private fun mainFunctionDetector(declaration: KtDeclaration) =
        MainFunctionDetector(declaration.languageVersionSettings) {
            it.resolveToDescriptorIfAny()
        }
}