// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.debugger.test

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.codegen.ClassBuilderFactory
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.debugger.test.DebuggerTestCompilerFacility
import org.jetbrains.kotlin.idea.debugger.test.TestFileWithModule
import java.io.File

internal class K2DebuggerTestCompilerFacility(
    private val project: Project,
    files: List<TestFileWithModule>,
    jvmTarget: JvmTarget,
    useIrBackend: Boolean
) : DebuggerTestCompilerFacility(files, jvmTarget, useIrBackend) {
    override fun compileTestSources(
        project: Project,
        srcDir: File,
        classesDir: File,
        classBuilderFactory: ClassBuilderFactory
    ): CompilationResult {
        return withTestServicesNeededForCodeCompilation(project) {
            super.compileTestSources(project, srcDir, classesDir, classBuilderFactory)
        }
    }

    override fun compileTestSources(module: Module, jvmSrcDir: File, commonSrcDir: File, classesDir: File, libClassesDir: File): String {
        return withTestServicesNeededForCodeCompilation(project) {
            super.compileTestSources(module, jvmSrcDir, commonSrcDir, classesDir, libClassesDir)
        }
    }
}