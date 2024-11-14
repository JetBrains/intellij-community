// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compose.k2.debugger.test.cases

import com.intellij.jarRepository.RemoteRepositoryDescription
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.compose.k2.test.K2ComposeTestProperties.COMPOSE_RUNTIME_MAVEN_COORDINATES
import org.jetbrains.kotlin.idea.debugger.test.DebuggerTestCompilerFacility
import org.jetbrains.kotlin.idea.debugger.test.TestCompileConfiguration
import org.jetbrains.kotlin.idea.debugger.test.TestFiles
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK2CodeKotlinEvaluateExpressionTest

/**
 * This test checks whether K2 CodeGen API uses `IrGenerationExtension`s of compose compiler plugins loading by IDE or not.
 * For example, if K2 CodeGen API internally loads `IrGenerationExtension`s from project extension area, the compiler plugins
 * loaded by `KotlinCompilerPluginsProvider` on IDE will not be used by K2 CodeGen API. In that case, this test must have
 * failures.
 */
abstract class AbstractK2ComposeDebuggerEvaluationTest : AbstractK2IdeK2CodeKotlinEvaluateExpressionTest() {
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
}