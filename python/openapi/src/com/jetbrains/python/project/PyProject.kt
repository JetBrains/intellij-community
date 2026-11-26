// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.jetbrains.python.project.PyProject.Companion.asPyProject
import com.jetbrains.python.project.PyProject.Companion.getPyProjects
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
   * Use [PyProject.project] extension if you need a project.
   * Do not check `isDisposed`: this class is guaranteed to be not disposed when used under read action.
   * Use [baseDir] to get a module root.
   * [PyProject.name] returns a module name.
   */
  @Deprecated("Module is deprecated API, do not use it.")
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
 * This module as a [PyProject] at its content root, also when it is no Python project yet: [PyProject.asPyProject] is
 * `null` then. For a UI that sets the first interpreter of the module, such as the root module of GoLand. It writes
 * nothing: the module becomes a Python project when it gets an interpreter. `null` for a module without a content root.
 * It reads the current workspace model and does not wait for the JPS model, so a modal dialog can call it.
 */
@ApiStatus.Internal
fun Module.asPyProjectCandidate(): PyProject? =
  ApplicationManager.getApplication().service<PyProjectService>().getPyProjectCandidate(this)

/**
 * The [PyProject] of this module for a caller that sets up its interpreter: [PyProject.asPyProject], else the module as
 * a candidate, see [asPyProjectCandidate]. The interpreter that the caller sets makes the module a Python project, as
 * the module then names it, so a module of GoLand needs no Python facet. `null` for a module without a content root.
 *
 * Other code, such as the status bar or an inspection, uses [PyProject.asPyProject]: a module without an interpreter
 * is no Python project there.
 */
@ApiStatus.Internal
suspend fun Module.asPyProjectOrCandidate(): PyProject? = asPyProject() ?: asPyProjectCandidate()

/**
 * The machine this project lives on.
 */
@get:ApiStatus.Internal
val PyProject.eelDescriptor: EelDescriptor get() = project.getEelDescriptor()

@ApiStatus.Internal
suspend fun PyProject.getEel(): EelApi = eelDescriptor.toEelApi()


@Suppress("DEPRECATION") // Temporary workaround
@get:ApiStatus.Internal
val PyProject.project: Project get() = residesOnModule.project

@Suppress("DEPRECATION") // Temporary workaround
@get:ApiStatus.Internal
val PyProject.name: @NlsSafe String get() = residesOnModule.name

