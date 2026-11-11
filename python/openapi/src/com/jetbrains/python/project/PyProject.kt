// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.jetbrains.python.project.impl.PyProjectService
import com.jetbrains.python.venvReader.Directory
import org.jetbrains.annotations.ApiStatus

/**
 * Python project which currently sits on top of [Module], but that might be changed soon.
 * Create it from a module or a project with [Module.asPyProject] or [Project.getPyProjects].
 * `null` means a module is either non-python or already disposed.
 *
 * The recommended approach is to get [PyProject] as fast, as possible, and build the whole logic on top of it.
 */
@ApiStatus.NonExtendable
interface PyProject {
  val baseDir: Directory

  /**
   * Do not use this field unless absolutely necessary.
   */
  val residesOnModule: Module

  companion object {
    /**
     * `null` means module is non-python or disposed and must be ignored
     *
     * Waits for the synchronization with the JPS model. Do not call it under a modal progress or from a modal dialog,
     * see [getPyProjectsWithoutJpsSync].
     */
    suspend fun Module.asPyProject(): PyProject? = service.getPyProject(this)

    /**
     * Waits for the synchronization with the JPS model. Do not call it under a modal progress or from a modal dialog,
     * see [getPyProjectsWithoutJpsSync].
     */
    suspend fun Project.getPyProjects(): List<PyProject> = service.getPyProjects(this)


    private val service get() = ApplicationManager.getApplication().service<PyProjectService>()
  }
}

/**
 * The [PyProject]s of the current workspace model, without the wait for the synchronization with the JPS model that
 * [PyProject.getPyProjects] makes. While the project opens, the workspace model can be the cache of the last session.
 *
 * For a reader that reads again on each module change, so the synchronization corrects it, and that a modal progress
 * or a modal dialog can hold up: they hold the synchronization back.
 */
@ApiStatus.Internal
fun Project.getPyProjectsWithoutJpsSync(): List<PyProject> =
  ApplicationManager.getApplication().service<PyProjectService>().getPyProjectsWithoutJpsSync(this)

/**
 * The machine this project lives on.
 */
@get:ApiStatus.Internal
val PyProject.eelDescriptor: EelDescriptor get() = project.getEelDescriptor()

@ApiStatus.Internal
suspend fun PyProject.getEel(): EelApi = eelDescriptor.toEelApi()
