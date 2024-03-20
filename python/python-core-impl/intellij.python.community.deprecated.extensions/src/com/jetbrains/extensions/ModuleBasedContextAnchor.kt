// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.extensions

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext
import com.jetbrains.python.psi.resolve.fromModule
import org.jetbrains.annotations.ApiStatus

/**
 * @deprecated moved to {@link com.jetbrains.python.extensions}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated(message = "Moved to com.jetbrains.python")
class ModuleBasedContextAnchor(val module: Module) : ContextAnchor {
  override val sdk: Sdk? = module.getSdk()
  override val project: Project = module.project
  override val qualifiedNameResolveContext: PyQualifiedNameResolveContext = fromModule(module)
  override val scope: GlobalSearchScope = module.moduleContentScope
  override fun getRoots(): Array<VirtualFile> {
    val manager = ModuleRootManager.getInstance(module)
    return super.getRoots() + manager.contentRoots + manager.sourceRoots
  }
}