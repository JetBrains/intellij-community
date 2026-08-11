package com.intellij.python.pyproject.model.internal.pyProject

import com.intellij.openapi.module.Module
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.internal.PYTHON_FACET_ID
import com.jetbrains.python.sdk.internal.PYTHON_MODULE_ID
import java.nio.file.Path

@ConsistentCopyVisibility
internal data class PyProjectImpl private constructor(override val baseDir: Path, override val residesOnModule: Module) : PyProject {
  internal companion object {
    fun create(module: Module, snapshot: ImmutableEntityStorage): PyProjectImpl? {
      val moduleEntity = module.findModuleEntity(snapshot) ?: return null
      if (moduleEntity.type?.name != PYTHON_MODULE_ID && moduleEntity.facets.none { it.typeId.name == PYTHON_FACET_ID }) {
        return null
      }
      val root = moduleEntity.contentRoots.firstOrNull() ?: return null
      return PyProjectImpl(root.url.toPath(), residesOnModule = module)
    }
  }
}