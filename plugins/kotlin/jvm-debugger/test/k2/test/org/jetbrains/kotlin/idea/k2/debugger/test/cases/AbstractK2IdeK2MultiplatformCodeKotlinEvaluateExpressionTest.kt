// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.debugger.test.cases

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.debugger.test.*

abstract class AbstractK2IdeK2MultiplatformCodeKotlinEvaluateExpressionTest: AbstractIrKotlinEvaluateExpressionInMppTest() {
    override val compileWithK2: Boolean = true
    override fun lambdasGenerationScheme() = JvmClosureGenerationScheme.INDY

    override fun createDebuggerTestCompilerFacility(
        testFiles: TestFiles,
        jvmTarget: JvmTarget,
        compileConfig: TestCompileConfiguration
    ): DebuggerTestCompilerFacility {
        return K2MppDebuggerCompilerFacility(project, testFiles, jvmTarget, compileConfig)
    }
}


private class K2MppDebuggerCompilerFacility(
    private val project: Project,
    files: List<TestFileWithModule>,
    jvmTarget: JvmTarget,
    compileConfig: TestCompileConfiguration,
) : MppDebuggerCompilerFacility(project, files, jvmTarget, compileConfig)