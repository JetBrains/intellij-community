// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.testFramework.utils.io.deleteRecursively
import com.intellij.util.io.createDirectories
import java.io.File
import java.nio.file.Path

internal object KotlinTestDirectoriesHolder {
    const val TRANSFORMED_KMP_LIBRARIES_DIR: String = "kotlin-transformed-test-libraries"

    val transformedKmpLibrariesRoot: Path by lazy {
        val transformedLibrariesPath = File(PathManager.getCommunityHomePath()).resolve("out").resolve(TRANSFORMED_KMP_LIBRARIES_DIR)
            .toPath().createDirectories()

        ShutDownTracker.getInstance().registerShutdownTask {
            transformedLibrariesPath.deleteRecursively()
        }

        transformedLibrariesPath
    }
}
