// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.python.project.impl.PyProjectService
import com.jetbrains.python.venvReader.Directory
import org.jetbrains.annotations.ApiStatus

/**
 * Python project which is currently sits on top of [Module], but that might be changed soon.
 * Create it from a module or a project with [Module.asPyProject] or [Project.getPyProjects].
 * `null` means a module is either non-python or already disposed.
 *
 * The recommended approach is to get [PyProject] as fast, as possible, and build the whole logic on top of it.
 */
@ApiStatus.NonExtendable
interface PyProject {
  val baseDir: Directory

  /**
   * Do not use this field unless absolutelly necessary.
   */
  val residesOnModule: Module

  companion object {
    /**
     * `null` means module is non-python or disposed and must be ignored
     */
    suspend fun Module.asPyProject(): PyProject? = service.getPyProject(this)

    suspend fun Project.getPyProjects(): List<PyProject> = service.getPyProjects(this)


    private val service get() = ApplicationManager.getApplication().service<PyProjectService>()
  }
}

