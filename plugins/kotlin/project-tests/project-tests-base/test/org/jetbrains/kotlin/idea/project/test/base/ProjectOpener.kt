// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project.test.base

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.kotlin.idea.performance.tests.utils.project.OpenProject
import org.jetbrains.kotlin.idea.performance.tests.utils.project.ProjectOpenAction

object ProjectOpener {
    fun openProject(projectData: ProjectData, jdk: Sdk): Project {
        val openProject = OpenProject(
            projectPath = projectData.path.toAbsolutePath().toString(),
            projectName = projectData.id,
            jdk = jdk,
            projectOpenAction = projectData.openAction,
        )
        return withGradleNativeSetToFalse(projectData.openAction) {
            ProjectOpenAction.openProject(openProject).also {
                openProject.projectOpenAction.postOpenProject(openProject = openProject, project = it)
            }
        }
    }

    private inline fun <R> withGradleNativeSetToFalse(openAction: ProjectOpenAction, body: () -> R): R {
        when (openAction) {
            ProjectOpenAction.GRADLE_PROJECT -> {
                val oldValue = System.getProperty(ORG_GRADLE_NATIVE)
                System.setProperty("org.gradle.native", "false")
                try {
                    return body()
                } finally {
                    when (oldValue) {
                        null -> System.clearProperty(ORG_GRADLE_NATIVE)
                        else -> System.setProperty(ORG_GRADLE_NATIVE, oldValue)
                    }
                }
            }
            else -> {
                return body()
            }
        }
    }

    private const val ORG_GRADLE_NATIVE = "org.gradle.native"

}