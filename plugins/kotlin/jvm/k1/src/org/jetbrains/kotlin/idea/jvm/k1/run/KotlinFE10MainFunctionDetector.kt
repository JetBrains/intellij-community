// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k1.run

import org.jetbrains.kotlin.idea.MainFunctionDetector
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.psi.KtNamedFunction

internal class KotlinFE10MainFunctionDetector : KotlinMainFunctionDetector {
    override fun isMain(function: KtNamedFunction, configuration: KotlinMainFunctionDetector.Configuration): Boolean {
        // copy-paste from MainFunctionDetector to avoid languageVersionSettings
        if (function.isLocal) {
            return false
        }

        var parametersCount = function.valueParameters.size
        if (function.receiverTypeReference != null) parametersCount++

        if (!isParameterNumberSuitsForMain(parametersCount, function.isTopLevel)) {
            return false
        }

        if (!function.typeParameters.isEmpty()) {
            return false
        }

        /* Psi only check for kotlin.jvm.jvmName annotation */
        if (KotlinMainFunctionDetector.MAIN_FUNCTION_NAME != function.name && !hasAnnotationWithExactNumberOfArguments(function, 1)) {
            return false
        }

        /* Psi only check for kotlin.jvm.jvmStatic annotation */
        if (configuration.checkJvmStaticAnnotation && !function.isTopLevel && !hasAnnotationWithExactNumberOfArguments(function, 0)) {
            return false
        }
        // end of copy-paste

        val languageVersionSettings = function.languageVersionSettings
        val mainFunctionDetector = MainFunctionDetector(languageVersionSettings) { it.resolveToDescriptorIfAny() }
        return mainFunctionDetector.isMain(
            function,
            checkJvmStaticAnnotation = configuration.checkJvmStaticAnnotation,
            allowParameterless = configuration.allowParameterless
        )
    }

    private fun isParameterNumberSuitsForMain(
        parametersCount: Int,
        isTopLevel: Boolean
    ) = when (parametersCount) {
        1 -> true
        0 -> isTopLevel // simplified version of MainFunctionDetector.isParameterNumberSuitsForMain
        else -> false
    }

    private fun hasAnnotationWithExactNumberOfArguments(function: KtNamedFunction, number: Int) =
        function.annotationEntries.any { it.valueArguments.size == number }
}