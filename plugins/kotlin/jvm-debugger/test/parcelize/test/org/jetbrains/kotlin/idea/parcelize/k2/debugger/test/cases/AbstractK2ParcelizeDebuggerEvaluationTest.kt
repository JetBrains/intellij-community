// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.parcelize.k2.debugger.test.cases

import com.intellij.jarRepository.RemoteRepositoryDescription
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.codegen.googleMavenRepository
import org.jetbrains.kotlin.idea.debugger.test.DebuggerTestCompilerFacility
import org.jetbrains.kotlin.idea.debugger.test.TestCompileConfiguration
import org.jetbrains.kotlin.idea.debugger.test.TestFiles
import org.jetbrains.kotlin.idea.k2.debugger.test.cases.AbstractK2IdeK2CodeKotlinEvaluateExpressionTest
import java.nio.file.Path

private const val PARCELIZE_RUNTIME_BAZEL_COORDINATES = "@kotlin_test_deps//:kotlin-parcelize-runtime-1.8.20.jar"

abstract class AbstractK2ParcelizeDebuggerEvaluationTest : AbstractK2IdeK2CodeKotlinEvaluateExpressionTest() {
    override fun createDebuggerTestCompilerFacility(
        testFiles: TestFiles,
        jvmTarget: JvmTarget,
        compileConfig: TestCompileConfiguration,
    ): DebuggerTestCompilerFacility {
        val facility = super.createDebuggerTestCompilerFacility(testFiles, jvmTarget, compileConfig)

        facility.addCompilerPlugin(parcelizeCompilerJars)
        addDependenciesByLabels(
            facility,
            listOf("classes(${PARCELIZE_RUNTIME_BAZEL_COORDINATES})"),
            emptyList()
        )

        return facility
    }

    // Downloading the parcelize runtime requires also specifying the Google repository.
    override fun jarRepositories(): List<RemoteRepositoryDescription> = listOf(
        RemoteRepositoryDescription.MAVEN_CENTRAL, googleMavenRepository
    )

    /**
     * We're looking up the 'parcelize compiler plugin' from the IntelliJ dependencies provided by kotlinc
     */
    private val parcelizeCompilerJars: List<Path> by lazy {
        listOf(TestKotlinArtifacts.parcelizeRuntime)
    }
}