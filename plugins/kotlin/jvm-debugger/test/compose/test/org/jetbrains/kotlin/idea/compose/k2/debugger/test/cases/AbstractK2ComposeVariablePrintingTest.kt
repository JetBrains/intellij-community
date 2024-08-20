// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compose.k2.debugger.test.cases

import com.intellij.jarRepository.RemoteRepositoryDescription
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.debugger.test.DebuggerTestCompilerFacility
import org.jetbrains.kotlin.idea.debugger.test.TestCompileConfiguration
import org.jetbrains.kotlin.idea.debugger.test.TestFiles
import org.jetbrains.kotlin.idea.compose.k2.debugger.test.K2ComposeTestProperties.COMPOSE_RUNTIME_MAVEN_COORDINATES
import org.jetbrains.kotlin.idea.compose.k2.debugger.test.composeCompilerJars
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK2CodeKotlinVariablePrintingTest

abstract class AbstractK2ComposeVariablePrintingTest : AbstractK2IdeK2CodeKotlinVariablePrintingTest() {
    override fun setUpModule() {
        super.setUpModule()
    }

    override fun createDebuggerTestCompilerFacility(
        testFiles: TestFiles,
        jvmTarget: JvmTarget,
        compileConfig: TestCompileConfiguration
    ): DebuggerTestCompilerFacility {
        val facility = super.createDebuggerTestCompilerFacility(testFiles, jvmTarget, compileConfig)

        facility.addCompilerPlugin(composeCompilerJars)
        addMavenDependency(facility, "maven(${COMPOSE_RUNTIME_MAVEN_COORDINATES})")

        return facility
    }

    /*
    Downloading the compose runtime requires also specifying the Google repository.
     */
    override fun jarRepositories(): List<RemoteRepositoryDescription> = listOf(
        RemoteRepositoryDescription.MAVEN_CENTRAL, RemoteRepositoryDescription(
            "google", "Google Maven Repository",
            "https://cache-redirector.jetbrains.com/dl.google.com.android.maven2"
        )
    )
}
