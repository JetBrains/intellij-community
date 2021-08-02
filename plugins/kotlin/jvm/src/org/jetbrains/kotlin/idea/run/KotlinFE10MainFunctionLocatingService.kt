package org.jetbrains.kotlin.idea.run

import org.jetbrains.kotlin.idea.MainFunctionDetector
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

internal class KotlinFE10MainFunctionLocatingService : KotlinMainFunctionLocatingService {
    override fun isMain(function: KtNamedFunction): Boolean {
        val bindingContext = function.analyze(BodyResolveMode.FULL)
        val mainFunctionDetector = MainFunctionDetector(bindingContext, function.languageVersionSettings)
        return mainFunctionDetector.isMain(function)
    }

    override fun hasMain(declarations: List<KtDeclaration>): Boolean {
        if (declarations.isEmpty()) return false

        val languageVersionSettings = declarations.first().languageVersionSettings
        val mainFunctionDetector =
          MainFunctionDetector(languageVersionSettings) { it.resolveToDescriptorIfAny(BodyResolveMode.FULL) }

        return mainFunctionDetector.hasMain(declarations)
    }
}