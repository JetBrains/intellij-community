// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compose.k2.debugger.test.cases

import com.intellij.jarRepository.RemoteRepositoryDescription
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.compose.k2.test.K2ComposeTestProperties.COMPOSE_RUNTIME_MAVEN_COORDINATES
import org.jetbrains.kotlin.idea.debugger.test.DebuggerTestCompilerFacility
import org.jetbrains.kotlin.idea.debugger.test.KotlinDescriptorTestCaseWithStepping
import org.jetbrains.kotlin.idea.debugger.test.TestCompileConfiguration
import org.jetbrains.kotlin.idea.debugger.test.TestFiles
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences

abstract class AbstractK2ComposeSteppingTest : KotlinDescriptorTestCaseWithStepping() {

    override fun createDebuggerTestCompilerFacility(
        testFiles: TestFiles,
        jvmTarget: JvmTarget,
        compileConfig: TestCompileConfiguration,
    ): DebuggerTestCompilerFacility {
        val facility = super.createDebuggerTestCompilerFacility(testFiles, jvmTarget, compileConfig)

        facility.addCompilerPlugin(composeCompilerJars)
        addMavenDependency(facility, "maven(${COMPOSE_RUNTIME_MAVEN_COORDINATES})")

        return facility
    }

    override fun jarRepositories(): List<RemoteRepositoryDescription> = jarRepositoriesForCompose()

    override fun doMultiFileTest(files: TestFiles, preferences: DebuggerPreferences) {
        repeat(countBreakpointsNumber(files.wholeFile)) {
            doOnBreakpoint {
                resume(this)
            }
        }
    }
}
