package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.ProjectStructureInfo
import com.intellij.python.pyproject.model.spi.PyProjectManager
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.python.pyproject.model.spi.TomlDependencySpecification
import com.intellij.python.pyproject.model.spi.resolveSrcRoots
import com.intellij.python.pyproject.safeGetArr
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.venvReader.Directory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.tuweni.toml.Toml
import org.apache.tuweni.toml.TomlTable
import java.io.IOException
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.writeText

internal class DefaultPyProjectManager : PyProjectManager {
  // Matches any flavor, so this manager must be the last one.
  // `intellij.python.pyproject.xml` registers it with `order="last"`.
  override val flavorDataType: Class<PythonSdkFlavor<*>> = PythonSdkFlavor::class.java

  override suspend fun createProject(
    where: Directory,
    name: @NlsSafe String?,
  ): PyResult<Unit> {
    val (dir, name) = if (name != null) Pair(where.resolve(name), name) else Pair(where, where.fileName.pathString)
    val fileName = dir.resolve(PY_PROJECT_TOML)
    val escapedName = Toml.tomlEscape(PyPackageName.normalizeProjectName(name))
    try {
      withContext(Dispatchers.IO) {
        fileName.parent.createDirectories()
        fileName.writeText("""
        [project]
        name = "$escapedName"
        version = "0.1.0"
      """.trimIndent())
      }
    }
    catch (e: IOException) {
      return PyResult.localizedError(PyProjectTomlBundle.message("cant.create.file", fileName, e))
    }
    return PyResult.success(Unit)

  }

  override val id: ToolId = ToolId("pip")
  override val ui: PyToolUIInfo = PyToolUIInfo("pip")

  override suspend fun getProjectStructure(
    entries: Map<ProjectName, PyProjectTomlProject>,
    rootIndex: Map<Directory, ProjectName>,
  ): ProjectStructureInfo? = null

  /**
   * `setuptools` is a build backend without a manager of its own, so the default manager reports its source roots.
   *
   * ```toml
   * [tool.setuptools.packages.find]
   * where = ["my_src", "my_other_src"]  # -> both directories
   * ```
   *
   * See PY-88898.
   */
  override suspend fun getSrcRoots(
    toml: TomlTable,
    projectRoot: Directory,
  ): Set<Directory> {
    val where = toml.safeGetArr<String>(SETUPTOOLS_PACKAGES_FIND_WHERE, unquotedDottedKey = true).successOrNull ?: return emptySet()
    return resolveSrcRoots(projectRoot, where)
  }

  override fun getTomlDependencySpecifications(): List<TomlDependencySpecification> = emptyList()
}

private const val SETUPTOOLS_PACKAGES_FIND_WHERE: String = "tool.setuptools.packages.find.where"
