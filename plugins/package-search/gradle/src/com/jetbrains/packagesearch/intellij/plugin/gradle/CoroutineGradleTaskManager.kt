// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.project.Project
import org.gradle.api.Task
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KClass

object CoroutineGradleTaskManager {

    /**
     * See [GradleTaskManager.runCustomTask].
     * @return `true` if task is successful, else `false`
     */
    suspend inline fun <reified T : Task> runCustomTask(
        project: Project,
        executionName: @Nls String,
        projectPath: String,
        gradlePath: String,
        progressExecutionMode: ProgressExecutionMode,
        taskConfiguration: String? = null,
        toolingExtensionClasses: Set<KClass<*>> = emptySet()
    ): Boolean = suspendCoroutine { continuation ->
        GradleTaskManager.runCustomTask(
            project,
            executionName,
            T::class.java,
            projectPath,
            gradlePath,
            taskConfiguration,
            progressExecutionMode,
            object : TaskCallback {
                override fun onSuccess() = continuation.resume(true)
                override fun onFailure() = continuation.resume(false)
            },
            toolingExtensionClasses.map { it.java }.toSet()
        )
    }

    suspend fun runTask(
        taskScript: String,
        taskName: String,
        project: Project,
        executionName: @Nls String,
        projectPath: String,
        gradlePath: String,
        progressExecutionMode: ProgressExecutionMode
    ): Boolean = suspendCoroutine { continuation ->
        GradleTaskManager.runCustomTaskScript(
            project,
            executionName,
            projectPath,
            gradlePath,
            progressExecutionMode,
            object : TaskCallback {
                override fun onSuccess() = continuation.resume(true)
                override fun onFailure() = continuation.resume(false)
            },
            taskScript,
            taskName
        )
    }
}