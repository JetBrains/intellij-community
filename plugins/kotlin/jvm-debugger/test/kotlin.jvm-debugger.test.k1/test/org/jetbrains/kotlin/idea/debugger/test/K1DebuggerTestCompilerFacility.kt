// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.cli.jvm.compiler.findMainClass
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.resolve.languageVersionSettings
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile

class K1DebuggerTestCompilerFacility(
    project: Project,
    files: List<TestFileWithModule>,
    jvmTarget: JvmTarget,
    compileConfig: TestCompileConfiguration,
): DebuggerTestCompilerFacility(project, files, jvmTarget, compileConfig) {
    companion object {
        // Returns the qualified name of the main test class.
        fun analyzeAndFindMainClass(project: Project, jvmKtFiles: List<KtFile>): String? {
            return runReadAction {
                val (languageVersionSettings, analysisResult) = analyzeSources(project, jvmKtFiles)
                findMainClass(analysisResult.bindingContext, languageVersionSettings, jvmKtFiles)?.asString()
            }
        }

        fun analyzeSources(project: Project, ktFiles: List<KtFile>): Pair<LanguageVersionSettings, AnalysisResult> {
            return runReadAction {
                val resolutionFacade = KotlinCacheService.Companion.getInstance(project)
                    .getResolutionFacadeWithForcedPlatform(ktFiles, JvmPlatforms.unspecifiedJvmPlatform)
                val analysisResult = try {
                    resolutionFacade.analyzeWithAllCompilerChecks(ktFiles)
                } catch (_: ProcessCanceledException) {
                    // allow module's descriptors update due to dynamic loading of Scripting Support Libraries for .kts files
                    resolutionFacade.analyzeWithAllCompilerChecks(ktFiles)
                }
                analysisResult.throwIfError()
                resolutionFacade.languageVersionSettings to analysisResult
            }
        }
    }
}