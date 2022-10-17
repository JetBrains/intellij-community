package org.jetbrains.kotlin.idea.run

import com.intellij.openapi.application.runReadAction
import org.jetbrains.kotlin.idea.MainFunctionDetector
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.psi.KtNamedFunction

internal class KotlinFE10MainFunctionDetector : KotlinMainFunctionDetector {
    override fun isMain(function: KtNamedFunction, configuration: KotlinMainFunctionDetector.Configuration): Boolean {
        val languageVersionSettings = function.languageVersionSettings
        val mainFunctionDetector = MainFunctionDetector(languageVersionSettings) { it.resolveToDescriptorIfAny() }
        return runReadAction { mainFunctionDetector.isMain(function, checkJvmStaticAnnotation = configuration.checkJvmStaticAnnotation) }
    }
}