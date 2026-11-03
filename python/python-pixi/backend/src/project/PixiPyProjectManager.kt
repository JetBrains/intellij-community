// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.project

import com.electronwill.nightconfig.toml.TomlFormat
import com.electronwill.nightconfig.toml.TomlWriter
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pixi.backend.PIXI_TOOL_ID
import com.intellij.python.pixi.backend.PixiPyTool
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.intellij.python.pixi.backend.sdk.PixiSdkFlavor
import com.intellij.python.pixi.backend.workspace.findPixiWorkspace
import com.intellij.python.pixi.backend.workspace.PixiWorkspaceOperations
import com.intellij.python.pixi.backend.workspace.parsePixiToml
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.PyProjectIssue
import com.intellij.python.pyproject.PyProjectTable
import com.intellij.python.pyproject.model.spi.ProjectDependencies
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.ProjectStructureInfo
import com.intellij.python.pyproject.model.spi.PyProjectManager
import com.intellij.python.pyproject.model.spi.PyProjectTomlProject
import com.intellij.python.pyproject.model.spi.TomlDependencySpecification
import com.intellij.python.pyproject.safeGet
import com.intellij.python.pytools.backend.runtime.PyToolRuntime
import com.intellij.python.pytools.backend.runtime.executeAndHandleErrors
import com.intellij.python.sdk.backend.PySdkBundle
import com.intellij.python.sdk.backend.resolveExecutable
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.venvReader.Directory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.tuweni.toml.TomlTable
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.toPath

internal class PixiPyProjectManager : PyProjectManager {
  override val id = PIXI_TOOL_ID
  override val ui = PyToolUIInfo(PyPixiBackendBundle.message("python.pixi.add.manager.name"), AllIcons.Nodes.Package)
  override val flavorDataType = PixiSdkFlavor::class.java
  override val dependencyGroupSupport = PixiDependencyGroupSupport

  override suspend fun createProject(where: Directory, name: @NlsSafe String?): PyResult<Unit> {
    val fileSystem = EelFileSystem(where.getEelDescriptor().toEelApi())
    val executable = fileSystem.resolveExecutable(PixiPyTool.getInstance())
                     ?: return PyResult.localizedError(PySdkBundle.message("cannot.find.executable", "pixi", fileSystem.userReadableName))
    val root = name?.let { where.resolve(it) } ?: where
    val workspace = withContext(Dispatchers.IO) { findPixiWorkspace(root) }
    if (workspace != null && workspace.root != root) {
      return PixiWorkspaceOperations.run(workspace.root) {
        val packageName = createWorkspacePackage(root).getOr { return@run it }
        val runtime = PyToolRuntime(fileSystem.getBinaryToExec(executable, workspace.root), ExecOptions())
        runtime.executeAndHandleErrors(
          "add", "--manifest-path", ".", "--pypi", "--editable",
          "--path=${root.relativeTo(workspace.root).pathString}", "--", packageName,
          transformer = ZeroCodeStdoutTransformer,
        ).mapSuccess { }
      }
    }
    withContext(Dispatchers.IO) {
      if (!root.resolve(PY_PROJECT_TOML).exists() && PyPackageName.normalizeProjectName(root.name).isEmpty()) {
        return@withContext PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.project.name.invalid"))
      }
      try {
        root.createDirectories()
        PyResult.success(Unit)
      }
      catch (e: IOException) {
        PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.project.manifest.failed", root, e.localizedMessage))
      }
    }.getOr { return it }
    return PixiWorkspaceOperations.run(root) {
      prepareProjectName(root).getOr { return@run it }
      val runtime = PyToolRuntime(fileSystem.getBinaryToExec(executable, where), ExecOptions())
      runtime.executeAndHandleErrors(
        "init", "--format", "pyproject", "--channel", "conda-forge", "--", name ?: ".",
        transformer = ZeroCodeStdoutTransformer,
      ).mapSuccess { }
    }
  }

  private suspend fun createWorkspacePackage(root: Directory): PyResult<String> = withContext(Dispatchers.IO) {
    val manifest = root.resolve(PY_PROJECT_TOML)
    val packageName = PyPackageName.normalizeProjectName(root.name)
    if (packageName.isEmpty()) {
      return@withContext PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.project.name.invalid"))
    }
    try {
      if (manifest.exists()) {
        val existingName = parsePixiToml(manifest.readText())?.getString("project.name")
        return@withContext existingName?.let { PyResult.success(it) }
                           ?: PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.project.name.invalid"))
      }
      root.createDirectories()
      writeProjectManifest(manifest, packageName, buildable = true)
      val sources = root.resolve("src").resolve(packageName.replace('-', '_')).createDirectories()
      Files.newByteChannel(sources.resolve("__init__.py"), CREATE, WRITE).use { }
    }
    catch (e: IOException) {
      return@withContext PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.project.manifest.failed",
                                                                             manifest,
                                                                             e.localizedMessage))
    }
    PyResult.success(packageName)
  }

  private suspend fun prepareProjectName(root: Directory): PyResult<Unit> = withContext(Dispatchers.IO) {
    val manifest = root.resolve(PY_PROJECT_TOML)
    if (manifest.exists()) return@withContext PyResult.success(Unit)
    val projectName = PyPackageName.normalizeProjectName(root.name)
    if (projectName == root.name) return@withContext PyResult.success(Unit)
    if (projectName.isEmpty()) {
      return@withContext PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.project.name.invalid"))
    }
    try {
      root.createDirectories()
      writeProjectManifest(manifest, projectName, buildable = false)
    }
    catch (_: FileAlreadyExistsException) {
      return@withContext PyResult.success(Unit)
    }
    catch (e: IOException) {
      return@withContext PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.project.manifest.failed",
                                                                             manifest,
                                                                             e.localizedMessage))
    }
    PyResult.success(Unit)
  }

  private fun writeProjectManifest(manifest: Path, projectName: String, buildable: Boolean) {
    val config = TomlFormat.newConfig { linkedMapOf() }
    config.add("project.name", projectName)
    config.add("project.version", "0.1.0")
    if (buildable) {
      config.add("project.dependencies", emptyList<String>())
      config.add("build-system.requires", listOf("hatchling"))
      config.add("build-system.build-backend", "hatchling.build")
    }
    Files.writeString(manifest, TomlWriter().writeToString(config), CREATE_NEW, WRITE)
  }

  override suspend fun getProjectStructure(
    entries: Map<ProjectName, PyProjectTomlProject>,
    rootIndex: Map<Directory, ProjectName>,
  ): ProjectStructureInfo = withContext(Dispatchers.IO) {
    val members = entries.mapNotNull { [name, entry] ->
      val workspace = findPixiWorkspace(entry.root) ?: return@mapNotNull null
      val owner = rootIndex[workspace.root] ?: return@mapNotNull null
      name to owner
    }.toMap()
    val dependencies = members.values.toSet().associateWith { workspaceName ->
      val root = entries.getValue(workspaceName).root
      val pixiManifest = root.resolve("pixi.toml")
      val manifest = if (pixiManifest.exists()) pixiManifest else root.resolve(PY_PROJECT_TOML)
      val toml = parsePixiToml(manifest.readText())
      val table = if (manifest == pixiManifest) toml else toml?.getTable("tool.pixi")
      val scopes = buildList {
        table?.let { add(it) }
        for (section in listOf("feature", "environments")) {
          val children = table?.getTable(section)
          for (name in children?.keySet().orEmpty()) {
            children?.getTable(listOf(name))?.let { add(it) }
          }
        }
      }
      val dependencyTables = scopes.flatMap { scope ->
        buildList {
          scope.getTable("pypi-dependencies")?.let { add(it) }
          val targets = scope.getTable("target")
          for (platform in targets?.keySet().orEmpty()) {
            targets?.getTable(listOf(platform))?.getTable("pypi-dependencies")?.let { add(it) }
          }
        }
      }
      dependencyTables.flatMap { dependencyTable ->
        dependencyTable.keySet().mapNotNull { dependency ->
          val path = dependencyTable.getTable(listOf(dependency))?.getString("path") ?: return@mapNotNull null
          try {
            rootIndex[root.resolve(path).normalize()]
          }
          catch (_: InvalidPathException) {
            null
          }
        }
      }.toSet() - workspaceName
    }.toMutableMap()
    for (memberName in members.keys) {
      val root = entries.getValue(memberName).root
      val groupDependencies = dependencyGroupPaths(root).mapNotNull { rootIndex[it] }.toSet() - memberName
      if (groupDependencies.isNotEmpty()) {
        dependencies[memberName] = dependencies[memberName].orEmpty() + groupDependencies
      }
    }
    ProjectStructureInfo(ProjectDependencies(dependencies), members)
  }

  override fun getAlternativeProjectTable(
    pyProjectToml: TomlTable,
    fallbackName: String,
    issues: MutableList<PyProjectIssue>,
  ): PyProjectTable? {
    val workspace = pyProjectToml.safeGet<TomlTable>("tool.pixi.workspace", true).successOrNull
                    ?: pyProjectToml.safeGet<TomlTable>("tool.pixi.project", true).successOrNull ?: return null
    val name = workspace.safeGet<String>("name", false).successOrNull ?: fallbackName
    return PyProjectTable.makeVirtProj(name)
  }

  override suspend fun getSrcRoots(toml: TomlTable, projectRoot: Directory): Set<Directory> = emptySet()

  override fun getTomlDependencySpecifications(): List<TomlDependencySpecification> = emptyList()
}

private fun dependencyGroupPaths(root: Path): Set<Path> {
  val groups = try {
    parsePixiToml(root.resolve(PY_PROJECT_TOML).readText())?.getTable("dependency-groups")
  }
  catch (_: IOException) {
    null
  } ?: return emptySet()
  return groups.keySet().asSequence()
    .flatMap { groups.getArray(listOf(it)).orEmpty().asSequence().filterIsInstance<String>() }
    .mapNotNull { resolvePythonPathDependency(root, it) }
    .toSet()
}

private fun resolvePythonPathDependency(root: Path, specification: String): Path? {
  val [_, reference] = PYTHON_PATH_DEPENDENCY.matchEntire(specification)?.destructured ?: return null
  return try {
    if (reference.startsWith("file:")) {
      URI(reference).toPath().normalize()
    }
    else {
      val [parents, relativePath] = HATCH_ROOT_URI.matchEntire(reference)?.destructured ?: return null
      val parentCount = parents.count { it == ':' }
      generateSequence(root) { it.parent }.drop(parentCount).firstOrNull()
        ?.resolve(relativePath.removePrefix("/"))?.normalize()
    }
  }
  catch (_: URISyntaxException) {
    null
  }
  catch (_: IllegalArgumentException) {
    null
  }
}

private val PYTHON_PATH_DEPENDENCY = """([\w-]+) @ (.*)""".toRegex()
private val HATCH_ROOT_URI = """\{root((?::parent)*):uri}(/.*)?""".toRegex()
