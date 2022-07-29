/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
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
        gradlePath: String
    ): Boolean = suspendCoroutine { continuation ->
        GradleTaskManager.runCustomTaskScript(
            project,
            executionName,
            projectPath,
            gradlePath,
            ProgressExecutionMode.IN_BACKGROUND_ASYNC,
            object : TaskCallback {
                override fun onSuccess() = continuation.resume(true)
                override fun onFailure() = continuation.resume(false)
            },
            taskScript,
            taskName
        )
    }
}