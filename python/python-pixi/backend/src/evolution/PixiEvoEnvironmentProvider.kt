// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.evolution

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.fs.EelFileUtils
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pixi.backend.PIXI_TOOL_ID
import com.intellij.python.pixi.backend.PixiPyTool
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.intellij.python.pixi.backend.workspace.findPixiWorkspace
import com.intellij.python.pixi.backend.workspace.PixiWorkspaceOperations
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pixi.backend.workspace.createPixiRuntime
import com.intellij.python.pixi.backend.workspace.resolvePixiWorkspacePath
import com.intellij.python.pixi.backend.workspace.pixiFileExists
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PySdkBundle
import com.intellij.python.sdk.backend.resolvePythonBinary
import com.intellij.python.sdk.backend.resolveExecutable
import com.intellij.python.sdk.backend.evolution.DiscoveredVenv
import com.intellij.python.sdk.backend.evolution.EvoRecreateSpec
import com.intellij.python.sdk.backend.evolution.EvoToolContext
import com.intellij.python.sdk.backend.evolution.PyEvoEnvironmentProvider
import com.intellij.python.sdk.backend.evolution.envNotFound
import com.intellij.python.sdk.backend.evolution.evoCreateEnvLeaf
import com.intellij.python.sdk.backend.evolution.evoEnvLeaf
import com.intellij.python.sdk.backend.evolution.toDisplayPath
import com.intellij.python.sdk.backend.evolution.toSectionLabel
import com.intellij.python.sdk.backend.evolution.toolMissing
import com.intellij.python.sdk.common.EvoRowAction
import com.intellij.python.sdk.common.PyEnvRef
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoRecreateDto
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoAddNewDto
import com.intellij.python.sdk.common.evolution.EvoAddNewOptionDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.EelOrTarget
import com.jetbrains.python.sdk.add.v2.FileSystemWithEel
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.evolution.deleteEnvDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import kotlin.io.path.isDirectory

internal class PixiEvoEnvironmentProvider : PyEvoEnvironmentProvider {
  override val tool: PyTool get() = PixiPyTool.getInstance()
  override val toolId: ToolId get() = PIXI_TOOL_ID
  override val label: String get() = PyPixiBackendBundle.message("python.pixi.evolution.node.label")
  override val icon get() = AllIcons.Nodes.Package
  override val stepDescription: String get() = PyPixiBackendBundle.message("python.pixi.evolution.node.step")

  override suspend fun loadSections(context: EvoToolContext, discovered: List<DiscoveredVenv>): EvoLoadResultDto {
    val workspace = findPixiWorkspace(context.workspace.baseDir) ?: return EvoLoadResultDto.Ok(emptyList())
    val leaves = workspace.environments.map { environmentName ->
      @NlsSafe val title: String = environmentName
      val envRef = PyEnvRef(environmentName)
      val pythonBinary = pythonBinaryOf(context.workspace.pyProject, envRef, context.fileSystem).getOrNull()?.path
      if (pythonBinary == null) evoCreateEnvLeaf(title, environmentName, icon)
      else evoEnvLeaf(title, pythonBinary, envRef)
    }
    return EvoLoadResultDto.Ok(listOf(EvoSectionDto(
      label = workspace.root.toSectionLabel(),
      labelTooltip = workspace.root.toDisplayPath(),
      leaves = leaves,
      addNew = true,
    )))
  }

  override suspend fun <P : PathHolder> pythonBinaryOf(
    pyProject: PyProject,
    envRef: PyEnvRef,
    fileSystem: FileSystem<P>,
  ): PyResult<P> {
    val workspace = findPixiWorkspace(pyProject.baseDir) ?: return envNotFound(envRef)
    if (envRef.value !in workspace.environments) return envNotFound(envRef)
    val executable = when (fileSystem.eelOrTarget) {
      is EelOrTarget.IsEel -> null
      is EelOrTarget.IsTarget -> fileSystem.resolveExecutable(tool) ?: return toolMissing()
    }
    val home = resolvePixiWorkspacePath(fileSystem, executable, workspace.root, ".pixi/envs/${envRef.value}").getOr { return it }
    val binary = fileSystem.resolvePythonBinary(home) ?: return envNotFound(envRef)
    return when (fileSystem.eelOrTarget) {
      is EelOrTarget.IsEel -> Result.success(binary)
      is EelOrTarget.IsTarget -> if (pixiFileExists(fileSystem, binary).getOr { return it }) Result.success(binary) else envNotFound(envRef)
    }
  }

  override suspend fun envDirectory(pyProject: PyProject, envRef: PyEnvRef, fileSystem: FileSystemWithEel): Path? {
    val workspace = findPixiWorkspace(pyProject.baseDir) ?: return null
    if (envRef.value !in workspace.environments) return null
    return fileSystem.resolveInWorkingDir(workspace.root, ".pixi/envs/${envRef.value}")?.path
  }

  override suspend fun createSdkForNewEnv(context: EvoToolContext, ref: EvoRowAction.CreateEnv): PyResult<PythonInterpreter> {
    val executable = executableOrNull(context.fileSystem) ?: return toolMissing()
    val workspace = findPixiWorkspace(context.workspace.baseDir) ?: return envNotFound(PyEnvRef(ref.token))
    val environment = ref.name ?: ref.token
    if (ref.name == null && environment !in workspace.environments) return envNotFound(PyEnvRef(environment))
    val runtime = createPixiRuntime(context.fileSystem, executable, workspace.root)
    PixiWorkspaceOperations.run(workspace.root) {
      if (ref.name != null) {
        createPixiNamedEnvironment(runtime, workspace.root, environment, ref.token).getOr { return@run it }
      }
      runtime.executeAndHandleErrors(
        "install", "--environment=$environment",
        transformer = ZeroCodeStdoutTransformer,
      )
    }.getOr { return it }
    return createInterpreter(context, interpreterRefOf(PyEnvRef(environment)))
  }

  override suspend fun addNewEnvSpec(context: EvoToolContext, section: EvoSectionDto): EvoAddNewDto? {
    val workspace = findPixiWorkspace(context.workspace.baseDir) ?: return null
    val takenNames = pixiReservedEnvironmentNames(workspace.root)
    val name = generateSequence(1) { it + 1 }.map { if (it == 1) "env" else "env-$it" }
      .first { it !in takenNames }
    return EvoAddNewDto(
      name = name, path = "", nameEditable = true, takenNames = takenNames,
      options = LanguageLevel.SUPPORTED_LEVELS.filter { it >= LanguageLevel.PYTHON38 }.reversed().map {
        EvoAddNewOptionDto(title = it.toPythonVersion(), token = it.toPythonVersion(), downloadedByTool = true)
      },
    )
  }

  override suspend fun recreateSpecFor(context: EvoToolContext, leaf: EvoLeafDto): EvoRecreateDto? {
    val action = leaf.action as? EvoRowAction.Select ?: return null
    val workspace = findPixiWorkspace(context.workspace.baseDir) ?: return null
    if (action.ref.manager != tool.fusId || action.ref.envRef.value !in workspace.environments) return null
    return EvoRecreateDto(listOf(EvoAddNewOptionDto(
      title = PyPixiBackendBundle.message("python.pixi.evolution.recreate.manifest"), token = "manifest",
    )))
  }

  override suspend fun recreateEnv(context: EvoToolContext, ref: PyInterpreterRef, spec: EvoRecreateSpec): PyResult<PythonInterpreter> {
    val executable = executableOrNull(context.fileSystem) ?: return toolMissing()
    val workspace = findPixiWorkspace(context.workspace.baseDir) ?: return envNotFound(ref.envRef)
    if (ref.manager != tool.fusId || ref.envRef.value !in workspace.environments || spec.baseToken != "manifest") {
      return envNotFound(ref.envRef)
    }
    val runtime = createPixiRuntime(context.fileSystem, executable, workspace.root)
    PixiWorkspaceOperations.run(workspace.root) {
      deletePixiEnvironment(workspace.root, ref.envRef).getOr { return@run it }
      runtime.executeAndHandleErrors(
        "install", "--environment=${ref.envRef.value}", transformer = ZeroCodeStdoutTransformer,
      )
    }.getOr { return it }
    return createInterpreter(context, ref)
  }
}

private suspend fun deletePixiEnvironment(root: Path, envRef: PyEnvRef): PyResult<Unit> = withContext(Dispatchers.IO) {
  if (!envRef.value.matches(Regex("[a-z0-9-]+"))) return@withContext envNotFound(envRef)
  val directory = root.resolve(".pixi/envs").resolve(envRef.value)
  try {
    val expected = root.toRealPath().resolve(".pixi/envs").resolve(envRef.value)
    if (directory.toRealPath() != expected || !directory.resolve("conda-meta").isDirectory(NOFOLLOW_LINKS)) {
      return@withContext envNotFound(envRef)
    }
    if (directory.resolvePythonBinary() != null) return@withContext deleteEnvDir(directory)
    EelFileUtils.deleteRecursively(directory)
    PyResult.success(Unit)
  }
  catch (e: AccessDeniedException) {
    fileLogger().warn("Cannot delete the Pixi environment at $directory", e)
    PyResult.localizedError(PySdkBundle.message("evolution.error.env.in.use", directory.toString()))
  }
  catch (e: IOException) {
    fileLogger().warn("Cannot delete the Pixi environment at $directory", e)
    PyResult.localizedError(PySdkBundle.message("evolution.error.env.delete.failed", directory.toString()))
  }
}
