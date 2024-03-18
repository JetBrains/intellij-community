// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.extensions

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.extensions.getSdk
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext
import com.jetbrains.python.psi.resolve.fromModule
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus
import java.util.*

/**
 * @deprecated moved to {@link com.jetbrains.python.extensions}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated(message = "Moved to com.jetbrains.python")
interface ContextAnchor {
  val sdk: Sdk?
  val project: Project
  val qualifiedNameResolveContext: PyQualifiedNameResolveContext?
  val scope: GlobalSearchScope
  fun getRoots(): Array<VirtualFile> {
    return sdk?.rootProvider?.getFiles(OrderRootType.CLASSES) ?: emptyArray()
  }
}

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

/**
 * @deprecated moved to {@link com.jetbrains.python.extensions}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated(message = "Moved to com.jetbrains.python")
data class QNameResolveContext(
  val contextAnchor: ContextAnchor,
  /**
   * Used for language level etc
   */
  val sdk: Sdk? = contextAnchor.sdk,

  val evalContext: TypeEvalContext,
  /**
   * If not provided resolves against roots only. Resolved also against this folder otherwise
   */
  val folderToStart: VirtualFile? = null,
  /**
   * Use index, plain dirs with Py2 and so on. May resolve names unresolvable in other cases, but may return false results.
   */
  val allowInaccurateResult: Boolean = false
)


/**
 * @deprecated moved to {@link com.jetbrains.python.extensions}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated(message = "Moved to com.jetbrains.python")
fun QualifiedName.getRelativeNameTo(root: QualifiedName): QualifiedName? {
  if (Collections.indexOfSubList(components, root.components) == -1) {
    return null
  }
  return subQualifiedName(root.componentCount, componentCount)
}

