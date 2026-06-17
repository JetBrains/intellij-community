package com.intellij.python.pyproject.model.internal

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.ProjectStructureInfo
import com.intellij.python.pyproject.model.spi.PyProjectManager
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.python.pyproject.model.spi.TomlDependencySpecification
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.venvReader.Directory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.tuweni.toml.Toml
import org.apache.tuweni.toml.TomlTable
import java.io.IOException
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

internal object DefaultPyProjectManager : PyProjectManager {
  override val additionalDataType: Class<PythonSdkAdditionalData> = PythonSdkAdditionalData::class.java


  override suspend fun createProject(
    where: Directory,
    name: @NlsSafe String,
  ): PyResult<Unit> {
    val fileName = where.resolve(name).resolve("pyproject.toml")
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

  override suspend fun getSrcRoots(
    toml: TomlTable,
    projectRoot: Directory,
  ): Set<Directory> = emptySet()

  override fun getTomlDependencySpecifications(): List<TomlDependencySpecification> = emptyList()
}
