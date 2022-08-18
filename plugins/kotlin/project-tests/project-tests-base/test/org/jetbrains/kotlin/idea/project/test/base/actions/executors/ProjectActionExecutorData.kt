// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project.test.base.actions.executors

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.project.test.base.ProjectBasedTestPreferences
import org.jetbrains.kotlin.idea.project.test.base.ProjectData
import org.jetbrains.kotlin.idea.project.test.base.actions.ProjectAction

data class ProjectActionExecutorData(
    val action: ProjectAction,
    val iteration: Int,
    val projectData: ProjectData,
    val project: Project,
    val profile: ProjectBasedTestPreferences,
)