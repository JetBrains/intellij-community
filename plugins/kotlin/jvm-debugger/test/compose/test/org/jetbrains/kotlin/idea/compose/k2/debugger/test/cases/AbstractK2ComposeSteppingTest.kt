// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compose.k2.debugger.test.cases

import com.intellij.jarRepository.RemoteRepositoryDescription
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.compose.k2.test.K2ComposeTestProperties.COMPOSE_RUNTIME_MAVEN_COORDINATES
import org.jetbrains.kotlin.idea.debugger.test.DebuggerTestCompilerFacility
import org.jetbrains.kotlin.idea.debugger.test.TestCompileConfiguration
import org.jetbrains.kotlin.idea.debugger.test.TestFiles
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK1CodeKotlinSteppingTest
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK2CodeKotlinSteppingTest

abstract class AbstractK2IdeK1CodeComposeSteppingTest : AbstractK2IdeK1CodeKotlinSteppingTest() {
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

abstract class AbstractK2IdeK2CodeComposeSteppingTest : AbstractK2IdeK2CodeKotlinSteppingTest() {
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

abstract class AbstractK2IdeK1CodeClassLambdaComposeSteppingTest : AbstractK2IdeK1CodeKotlinSteppingTest() {
    override fun lambdasGenerationScheme(): JvmClosureGenerationScheme = JvmClosureGenerationScheme.CLASS

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
