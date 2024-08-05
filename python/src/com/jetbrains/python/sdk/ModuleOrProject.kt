// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

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
sealed class ModuleOrProject(val project: Project) {
  class ProjectOnly(project: Project) : ModuleOrProject(project)
  class ModuleAndProject(val module: Module) : ModuleOrProject(module.project)
}