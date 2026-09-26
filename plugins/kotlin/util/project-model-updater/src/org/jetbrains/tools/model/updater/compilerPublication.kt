// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.isDirectory
import kotlin.system.exitProcess

internal fun publishCompiler(preferences: GeneratorPreferences) {
    val kotlinRepoRoot = preferences.kotlinCompilerRepositoryRoot
    println("Kotlin compiler repository: $kotlinRepoRoot")

    val kotlinSnapshotPath = KotlinTestsDependenciesUtil.kotlinCompilerSnapshotPath

    println("Artifacts path: $kotlinSnapshotPath")
    println("Publishing Kotlin compiler...")

    val exitCode = ProcessBuilder(buildList {
        addAll(gradleWrapperExecutable)
        add("publishIdeArtifacts")
        add(":prepare:ide-plugin-dependencies:kotlin-dist-for-ide:publish")
        if (preferences.publishGradlePlugin == true) {
            add("publishGradlePluginArtifacts")
        }

        add("-Ppublish.ide.plugin.dependencies=true")
        add("-PdeployVersion=$BOOTSTRAP_VERSION")
        add("-Pbuild.number=$BOOTSTRAP_VERSION")
        add("-Pkotlin.build.deploy-path=$kotlinSnapshotPath")
    })
        .directory(kotlinRepoRoot.toFile())
        .inheritIO()
        .start()
        .waitFor()

    if (exitCode != 0) {
        exitProcess(exitCode)
    }

    println("Successfully published to $kotlinSnapshotPath")
}

/**
 * The root of the Kotlin compiler repository.
 */
internal val GeneratorPreferences.kotlinCompilerRepositoryRoot: Path
    get() {
        val kotlinCompilerRepoPath = kotlinCompilerRepoPath ?: run {
            val defaultPath = ".."
            println("No '${GeneratorPreferences::kotlinCompilerRepoPath.name}' preference is provided; using the default '$defaultPath'")
            defaultPath
        }

        return KotlinTestsDependenciesUtil.projectRoot
            .resolve(kotlinCompilerRepoPath)
            .also {
                if (!it.isDirectory()) {
                    exitWithErrorMessage("Kotlin compiler repository path does not exist or is not a directory: $it")
                }
            }
            .absolute()
            .normalize()
    }

private val gradleWrapperExecutable: List<String>
    get() {
        val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        return if (isWindows) listOf("cmd", "/c", "gradlew.bat") else listOf("./gradlew")
    }
