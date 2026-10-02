// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.jetbrains.python.project.PyProject
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Lots of actions might be run on the project level or module level.
 * [project] is always exist (though it could be a default project), module is optional.
 * If a module exists, a project is always a module's project and never default.
 *
 * This class must be used instead of separate [Project], `Module` fields as it guarantees that project is module's project and never aother.
 *
 * So, instead of
 * ```kotlin
 * fun foo(module:Module?, project:Project) {
 *   if (module != null) {
 *   //what if module.project != project??
 *   }
 * }
 * ```
 * use
 * ```kotlin
 * foo (moduleOrProject:ModuleOrProject) {
 *    moduleOrProject.project // is always correct
 *    when(moduleOrProject) {
 *     is ProjectOnly -> //no module
 *     is ProjectAndModule -> moduleOrProject.module
 *    }
 * }
 *
 */
@ApiStatus.Experimental
sealed class ModuleOrProject(val project: Project) {
  class ProjectOnly(project: Project) : ModuleOrProject(project)


  /**
   * [module] always exists, [pyProject] is prefered if exists: [module] will be dropped soon.
   * It is guaranteed to be based on the same [module].
   */
  class ModuleAndProject
  private constructor(val module: Module, @get:ApiStatus.Internal val pyProject: PyProject?) : ModuleOrProject(module.project) {
    @ApiStatus.Internal
    constructor(pyProject: PyProject) : this(pyProject.residesOnModule, pyProject)

    /**
     * Use the one with [PyProject]
     */
    @ApiStatus.Obsolete
    constructor(module: Module) : this(module, null)
  }
}

@get:ApiStatus.Internal
val ModuleOrProject.moduleIfExists: Module?
  get() = when (this) {
    is ModuleOrProject.ModuleAndProject -> module
    is ModuleOrProject.ProjectOnly -> null
  }

@get:ApiStatus.Internal
val ModuleOrProject.workingDirectory: Path?
  get() = when (this) {
    is ModuleOrProject.ModuleAndProject -> pyProject?.baseDir ?: module.baseDir?.toNioPath()
    is ModuleOrProject.ProjectOnly -> project.basePath?.let { Path.of(it) }
  }

@get:ApiStatus.Internal
val ModuleOrProject.destructured: Pair<Project, Module?>
  get() = when (this) {
    is ModuleOrProject.ProjectOnly -> project to null
    is ModuleOrProject.ModuleAndProject -> project to module
  }

@get:ApiStatus.Internal
val Module.asModuleOrProject: ModuleOrProject.ModuleAndProject get() = ModuleOrProject.ModuleAndProject(this)

@get:ApiStatus.Internal
val Project.asModuleOrProject: ModuleOrProject.ProjectOnly get() = ModuleOrProject.ProjectOnly(this)
