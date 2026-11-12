package com.intellij.python.pyproject.model.internal.pyProject

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.python.pyproject.model.evolution.namesInterpreter
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import com.intellij.util.PlatformUtils
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.internal.PYTHON_FACET_ID
import com.jetbrains.python.sdk.internal.PYTHON_MODULE_ID
import java.nio.file.InvalidPathException
import java.nio.file.Path

@ConsistentCopyVisibility
internal data class PyProjectImpl private constructor(override val baseDir: Path, override val residesOnModule: Module) : PyProject {
  internal companion object {
    private val log = fileLogger()
    fun create(module: Module, snapshot: ImmutableEntityStorage): PyProjectImpl? {
      val moduleEntity = module.findModuleEntity(snapshot) ?: return null
      return baseDirOf(moduleEntity)?.let { PyProjectImpl(it, residesOnModule = module) }
    }

    /**
     * The base dir of the PyProject on [moduleEntity], or `null` when the module is not a Python project. A module with a
     * content root is a Python project when one of these is true:
     * * its type is Python;
     * * it has a Python facet;
     * * the IDE is PyCharm, where the default module type is Python. A module without a type, or of another type, such
     *   as one that another IDE made, is then a Python project too. The check does not read the default module type:
     *   `ModuleTypeManager` off the EDT can deadlock on the class initialization of `ModuleType`, see
     *   [IJPL-261327](https://youtrack.jetbrains.com/issue/IJPL-261327);
     * * it names an interpreter, see [namesInterpreter]. A module of another IDE, such as a Java module, then is one.
     */
    fun baseDirOf(moduleEntity: ModuleEntity): Path? {
      if (!isPythonProject(moduleEntity)) return null
      return baseDirOfRoots(moduleEntity)
    }

    /** Whether [moduleEntity] is a Python project, see [baseDirOf]. */
    private fun isPythonProject(moduleEntity: ModuleEntity): Boolean =
      moduleEntity.type?.name == PYTHON_MODULE_ID ||
      moduleEntity.facets.any { it.typeId.name == PYTHON_FACET_ID } ||
      // Not the default module type of `ModuleTypeManager`: off the EDT it can deadlock, see IJPL-261327.
      PlatformUtils.isPyCharm() ||
      namesInterpreter(moduleEntity)

    /**
     * The base dir of [moduleEntity]: its content root, or the first of several. `null` for a module without one. It does
     * not check that the module is a Python project, see [baseDirOf].
     */
    fun baseDirOfRoots(moduleEntity: ModuleEntity): Path? {
      val roots = moduleEntity.contentRoots
      val root = when (val n = roots.size) {
        0 -> return null
        1 -> roots.first()
        else -> {
          // Several content roots are legitimate: a build system may root a module per source file rather than per
          // directory, which is what Bazel does for a target declaring no `imports` (see BazelPythonWorkspaceImporter).
          // Hence a warning and not an error report -- every root belongs to the same module, so the deterministic
          // first one is a safe base dir, and reporting an error here would fail the sync of any such project.
          log.warn("Python module has more than one content root, falling back to the first one: module=${moduleEntity.name}, roots=$n")
          // By the whole URL and not by the file name: two roots can share a name (`/a/src` and `/b/src`), and the
          // whole URL is unique, so the pick stays the same between runs.
          roots.minBy { it.url.url }
        }
      }
      // A URL that is no valid local path, such as one with a character that Windows rejects, gives no Python project.
      return try {
        root.url.toPath()
      }
      catch (e: InvalidPathException) {
        log.warn("Python module has a content root that is not a valid path: module=${moduleEntity.name}, root=${root.url.url}", e)
        null
      }
    }
  }
}