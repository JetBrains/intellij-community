package com.intellij.python.pyproject.model.internal.pyProject

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.Module
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.python.pyproject.model.evolution.namesInterpreter
import com.intellij.python.pyproject.model.internal.workspaceBridge.pyProjectTomlEntity
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import com.intellij.util.PlatformUtils
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.python.project.PyProject
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

    /** [module] as a PyProject at its content root, also when it is no Python project yet, see [baseDirOfRoots]. */
    fun createCandidate(module: Module, snapshot: ImmutableEntityStorage): PyProjectImpl? {
      val moduleEntity = module.findModuleEntity(snapshot) ?: return null
      return baseDirOfRoots(moduleEntity)?.let { PyProjectImpl(it, residesOnModule = module) }
    }

    /**
     * The base dir of the PyProject on [moduleEntity], or `null` when the module is not a Python project. A module with a
     * content root is a Python project when one of these is true:
     * * the IDE is PyCharm, where every module is a Python project;
     * * the `pyproject.toml` model made the module from a `pyproject.toml`, see [pyProjectTomlEntity];
     * * it has the Python module type, which an importer such as Bazel still writes, so the importer can give it an
     *   interpreter. The migration removes this type from a module of an `.iml`, see `migrateSdkReferences`;
     * * it names an interpreter, see [namesInterpreter]. A module of another IDE, such as a Java module, then is one.
     *
     * The Python facet does not count.
     */
    fun baseDirOf(moduleEntity: ModuleEntity): Path? {
      if (!isPythonProject(moduleEntity)) return null
      return baseDirOfRoots(moduleEntity)
    }

    /** Whether [moduleEntity] is a Python project, see [baseDirOf]. It does not check that the module has a content root. */
    fun isPythonProject(moduleEntity: ModuleEntity): Boolean =
      PlatformUtils.isPyCharm() || moduleEntity.pyProjectTomlEntity != null || moduleEntity.type?.name == PYTHON_MODULE_ID ||
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