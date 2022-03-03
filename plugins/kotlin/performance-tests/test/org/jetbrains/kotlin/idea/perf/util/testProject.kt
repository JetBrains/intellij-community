// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.perf.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.impl.ProjectLoadingErrorsHeadlessNotifier
import org.jetbrains.kotlin.idea.testFramework.ProjectOpenAction
import java.io.File

class ExternalProject(val path: String, val openWith: ProjectOpenAction) {
    companion object {
        val KOTLIN_PROJECT_PATH = run {
            val path = System.getProperty("performanceProjects", "kotlin")
            if (File(path).exists()) path else "../$path"
        }

        val KOTLIN_GRADLE = ExternalProject(KOTLIN_PROJECT_PATH, ProjectOpenAction.GRADLE_PROJECT)
        val KOTLIN_JPS = ExternalProject(KOTLIN_PROJECT_PATH, ProjectOpenAction.EXISTING_IDEA_PROJECT)

        // not intended for using in unit tests, only for local verification
        val KOTLIN_AUTO = ExternalProject(KOTLIN_PROJECT_PATH, autoOpenAction(KOTLIN_PROJECT_PATH))

        fun autoOpenAction(path: String): ProjectOpenAction {
            return when {
                exists(path, "build.gradle") || exists(path, "build.gradle.kts")  -> ProjectOpenAction.GRADLE_PROJECT
                exists(path, ".idea", "modules.xml") -> ProjectOpenAction.EXISTING_IDEA_PROJECT
                else -> ProjectOpenAction.GRADLE_PROJECT
            }
        }

        fun autoOpenProject(path: String): ExternalProject = ExternalProject(path, autoOpenAction(path))
    }
}

internal fun Disposable.registerLoadingErrorsHeadlessNotifier() {
    ProjectLoadingErrorsHeadlessNotifier.setErrorHandler(this) { description ->
        throw RuntimeException(description.description)
    }

}
