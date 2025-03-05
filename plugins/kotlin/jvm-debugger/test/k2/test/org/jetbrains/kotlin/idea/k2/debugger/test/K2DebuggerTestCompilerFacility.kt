// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.debugger.test

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.debugger.test.DebuggerTestCompilerFacility
import org.jetbrains.kotlin.idea.debugger.test.TestCompileConfiguration
import org.jetbrains.kotlin.idea.debugger.test.TestFileWithModule
import org.jetbrains.kotlin.psi.KtFile

internal class K2DebuggerTestCompilerFacility(
    private val project: Project,
    files: List<TestFileWithModule>,
    jvmTarget: JvmTarget,
    compileConfig: TestCompileConfiguration,
) : DebuggerTestCompilerFacility(project, files, jvmTarget, compileConfig) {

    override fun analyzeSources(ktFiles: List<KtFile>): Pair<LanguageVersionSettings, AnalysisResult> {
        return withTestServicesNeededForCodeCompilation(project) {
            super.analyzeSources(ktFiles)
        }
    }
}