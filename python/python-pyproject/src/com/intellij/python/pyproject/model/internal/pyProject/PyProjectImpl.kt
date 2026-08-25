package com.intellij.python.pyproject.model.internal.pyProject

import com.intellij.openapi.diagnostic.fileLogger
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
    private val log = fileLogger()
    fun create(module: Module, snapshot: ImmutableEntityStorage): PyProjectImpl? {
      val moduleEntity = module.findModuleEntity(snapshot) ?: return null
      if (moduleEntity.type?.name != PYTHON_MODULE_ID && moduleEntity.facets.none { it.typeId.name == PYTHON_FACET_ID }) {
        return null
      }
      val roots = moduleEntity.contentRoots
      val root = when (val n = roots.size) {
        0 -> return null
        1 -> roots.first()
        else -> {
          // Several content roots are legitimate: a build system may root a module per source file rather than per
          // directory, which is what Bazel does for a target declaring no `imports` (see BazelPythonWorkspaceImporter).
          // Hence a warning and not an error report -- every root belongs to the same module, so the deterministic
          // first one is a safe base dir, and reporting an error here would fail the sync of any such project.
          log.warn("Python module has more than one content root, falling back to the first one: module=${module.name}, roots=$n")
          roots.minBy { it.url.fileName } // We sort it to make it predictable between runs
        }
      }
      return PyProjectImpl(root.url.toPath(), residesOnModule = module)
    }
  }
}