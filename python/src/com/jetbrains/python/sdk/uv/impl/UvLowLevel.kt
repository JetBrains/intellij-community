// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.impl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.RuntimeJsonMappingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.value.constant
import com.intellij.execution.target.value.getRelativeTargetPath
import com.intellij.openapi.module.Module
import com.intellij.platform.eel.provider.localEel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.ExecError
import com.jetbrains.python.errorProcessing.ExecErrorReason
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.PyExecResult
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.uv.ScriptSyncCheckResult
import com.jetbrains.python.sdk.uv.UvCli
import com.jetbrains.python.sdk.uv.UvLowLevel
import com.jetbrains.python.venvReader.VirtualEnvReader
import com.jetbrains.python.venvReader.tryResolvePath
import io.github.z4kn4fein.semver.Version
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString

private const val NO_METADATA_MESSAGE = "does not contain a PEP 723 metadata tag"
private const val OUTDATED_ENV_MESSAGE = "The environment is outdated"
private val versionRegex = Regex("(\\d+\\.\\d+)\\.\\d+-.+\\s")

private class UvLowLevelImpl<P : PathHolder>(private val cwd: Path, private val venvPath: P?, private val uvCli: UvCli<P>, private val fileSystem: FileSystem<P>) : UvLowLevel<P> {
  override suspend fun initializeEnvironment(init: Boolean, version: Version?): PyResult<P> {
    val addPythonArg: (MutableList<String>) -> Unit = { args ->
      version?.let {
        args.add("--python")
        args.add("${version.major}.${version.minor}")
      }
    }

    if (init) {
      val initArgs = mutableListOf("init")
      addPythonArg(initArgs)
      initArgs.add("--bare")
      if (cwd.name.isNotBlank()) {
        initArgs.add("--name")
        initArgs.add(cwd.name)
      }
      initArgs.add("--no-project")
      uvCli.runUv(cwd, null, true, *initArgs.toTypedArray()).getOr { return it }
    }

    val venvArgs = mutableListOf("venv")
    venvPath?.also { venvArgs += it.toString() }
    addPythonArg(venvArgs)
    uvCli.runUv(cwd, null, true, *venvArgs.toTypedArray())
      .getOr { return it }

    // TODO PY-87712 Would be great to get rid of unsafe casts
    val path: P? = when (fileSystem) {
      is FileSystem.Eel -> {
        VirtualEnvReader().findPythonInPythonRoot((venvPath as? PathHolder.Eel)?.path ?: cwd.resolve(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME))
          ?.let { fileSystem.resolvePythonBinary(PathHolder.Eel(it)) } as P?
      }
      is FileSystem.Target -> {
        val pythonBinary = if (venvPath == null) {
          val targetPath = constant(cwd.pathString)
          val venvPath = targetPath.getRelativeTargetPath(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME)
          venvPath.apply(fileSystem.targetEnvironmentConfiguration.createEnvironmentRequest(project = null).prepareEnvironment(TargetProgressIndicator.EMPTY))
        } else venvPath.toString()
        fileSystem.resolvePythonBinary(PathHolder.Target(pythonBinary)) as P?
      }
    }

    if (path == null) {
      return PyResult.localizedError(PyBundle.message("python.sdk.uv.failed.to.initialize.uv.environment"))
    }

    return PyResult.success(path)
  }

  override suspend fun listUvPythons(): PyResult<Set<Path>> {
    var out = uvCli.runUv(cwd, venvPath, false, "python", "dir")
      .getOr { return it }

    val uvDir = tryResolvePath(out)
    if (uvDir == null) {
      return PyResult.localizedError(PyBundle.message("python.sdk.uv.failed.to.detect.uv.python.directory"))
    }

    // TODO: ask for json output format
    out = uvCli.runUv(cwd, venvPath, false, "python", "list", "--only-installed")
      .getOr { return it }

    val pythons = UvOutputParser.parseUvPythonList(uvDir, out)
    return PyResult.success(pythons)
  }

  override suspend fun listSupportedPythonVersions(versionRequest: String?): PyResult<List<Version>> {
    val args = mutableListOf("python", "list")

    if (versionRequest != null) {
      args += versionRequest
    }

    val out = uvCli.runUv(cwd, venvPath, false, *args.toTypedArray()).getOr { return it }
    val matches = versionRegex.findAll(out)

    return PyResult.success(
      matches.map {
        Version.parse(
          it.groupValues[1],
          strict = false
        )
      }
        .toSet()
        .toList()
        .sortedDescending()
    )
  }

  override suspend fun listPackages(): PyResult<List<PythonPackage>> {
    val out = uvCli.runUv(cwd, venvPath, false, "pip", "list", "--format", "json")
      .getOr { return it }

    data class PackageInfo(val name: String, val version: String)

    val mapper = jacksonObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    val packages = mapper.readValue<List<PackageInfo>>(out).map {
      PythonPackage(it.name, it.version, false)
    }

    return PyExecResult.success(packages)
  }

  override suspend fun listOutdatedPackages(): PyResult<List<PythonOutdatedPackage>> {
    val out = uvCli.runUv(cwd, venvPath, false, "pip", "list", "--outdated", "--format", "json")
      .getOr { return it }

    data class OutdatedPackageInfo(val name: String, val version: String, val latest_version: String)

    try {
      val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      val packages = mapper.readValue<List<OutdatedPackageInfo>>(out).map {
        PythonOutdatedPackage(it.name, it.version, it.latest_version)
      }

      return PyExecResult.success(packages)
    }
    catch (e: RuntimeJsonMappingException) {
      return PyResult.localizedError(e.message ?: e.localizedMessage ?: e.toString())
    }
  }

  override suspend fun listTopLevelPackages(module: Module): PyResult<List<PythonPackage>> {
    val args = mutableListOf("tree", "--depth=1", "--frozen", "--package", module.name)
    val out = uvCli.runUv(cwd, venvPath, false, *args.toTypedArray())
      .getOr { return it }

    return PyExecResult.success(UvOutputParser.parseUvPackageList(out))
  }

  override suspend fun listPackageRequirements(name: PythonPackage): PyResult<List<PyPackageName>> {
    val out = uvCli.runUv(cwd, venvPath, false, "pip", "show", name.name)
      .getOr { return it }

    return PyExecResult.success(UvOutputParser.parseUvPackageRequirements(out))
  }

  override suspend fun listPackageRequirementsTree(name: PythonPackage): PyResult<String> {
    val out = uvCli.runUv(cwd, venvPath, false, "tree", "--package", name.name, "--frozen")
      .getOr { return it }

    return PyExecResult.success(out)
  }

  override suspend fun listProjectStructureTree(): PyResult<String> {
    val out = uvCli.runUv(cwd, venvPath, false, "tree", "--frozen")
      .getOr { return it }

    return PyExecResult.success(out)
  }

  override suspend fun listAllPackagesTree(): PyResult<String> {
    val out = uvCli.runUv(cwd, venvPath, false, "pip", "tree")
      .getOr { return it }

    return PyExecResult.success(out)
  }

  override suspend fun installPackage(name: PythonPackageInstallRequest, options: List<String>): PyResult<Unit> {
    uvCli.runUv(cwd, venvPath, true, "pip", "install", *name.formatPackageName(), *options.toTypedArray())
      .getOr { return it }

    return PyExecResult.success(Unit)
  }

  override suspend fun uninstallPackages(pyPackages: Array<out String>): PyResult<Unit> {
    // TODO: check if package is in dependencies and reject it
    uvCli.runUv(cwd, venvPath, true, "pip", "uninstall", *pyPackages)
      .getOr { return it }

    return PyExecResult.success(Unit)
  }

  override suspend fun addDependency(pyPackages: PythonPackageInstallRequest, options: List<String>, workspaceMember: PyWorkspaceMember?): PyResult<Unit> {
    val args = mutableListOf("add")
    if (workspaceMember != null) {
      args.add("--package")
      args.add(workspaceMember.name)
    }
    args.addAll(pyPackages.formatPackageName())
    args.addAll(options)
    uvCli.runUv(cwd, venvPath, true, *args.toTypedArray())
      .getOr { return it }

    return PyExecResult.success(Unit)
  }

  override suspend fun removeDependencies(pyPackages: Array<out String>, workspaceMember: PyWorkspaceMember?): PyResult<Unit> {
    val args = mutableListOf("remove")
    if (workspaceMember != null) {
      args.add("--package")
      args.add(workspaceMember.name)
    }
    args.addAll(pyPackages)

    uvCli.runUv(cwd, venvPath, true, *args.toTypedArray())
      .getOr { return it }

    return PyExecResult.success(Unit)
  }

  override suspend fun isProjectSynced(inexact: Boolean): PyResult<Boolean> {
    val args = constructSyncArgs(inexact)

    uvCli.runUv(cwd, venvPath, false, *args.toTypedArray())
      .onFailure {
        val stderr = tryExtractStderr(it)

        if (stderr?.contains(OUTDATED_ENV_MESSAGE) == true) {
          return PyExecResult.success(false)
        }

        return PyExecResult.failure(it)
      }

    return PyExecResult.success(true)
  }

  override suspend fun isScriptSynced(inexact: Boolean, scriptPath: Path): PyResult<ScriptSyncCheckResult> {
    val args = constructSyncArgs(inexact) + listOf("--script", scriptPath.pathString)

    uvCli.runUv(cwd, venvPath, false, *args.toTypedArray())
      .onFailure {
        val stderr = tryExtractStderr(it)

        if (stderr?.contains(NO_METADATA_MESSAGE) == true) {
          return PyExecResult.success(ScriptSyncCheckResult.NoInlineMetadata)
        }

        if (stderr?.contains(OUTDATED_ENV_MESSAGE) == true) {
          return PyExecResult.success(ScriptSyncCheckResult.NotSynced)
        }

        return PyExecResult.failure(it)
      }

    return PyExecResult.success(ScriptSyncCheckResult.Synced)
  }

  fun constructSyncArgs(inexact: Boolean): MutableList<String> {
    val args = mutableListOf("sync", "--check", "--all-packages")

    if (inexact) {
      args += "--inexact"
    }

    return args
  }

  fun PythonPackageInstallRequest.formatPackageName(): Array<String> = when (this) {
    is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications -> specifications.map { it.nameWithVersionsSpec }.toTypedArray()
    is PythonPackageInstallRequest.ByLocation -> error("UV does not support installing from location uri")
  }

  override suspend fun sync(): PyResult<String> {
    return uvCli.runUv(cwd, venvPath, true, "sync", "--all-packages")
  }

  override suspend fun lock(): PyResult<String> {
    return uvCli.runUv(cwd, venvPath, true, "lock")
  }
}

fun createUvLowLevelLocal(cwd: Path, uvCli: UvCli<PathHolder.Eel>): UvLowLevel<PathHolder.Eel> =
  createUvLowLevel(cwd, uvCli, FileSystem.Eel(localEel), null)

fun <P : PathHolder> createUvLowLevel(cwd: Path, uvCli: UvCli<P>, fileSystem: FileSystem<P>, venvPath: P?): UvLowLevel<P> =
  UvLowLevelImpl(cwd, venvPath, uvCli, fileSystem)

suspend fun createUvLowLevelLocal(cwd: Path): PyResult<UvLowLevel<PathHolder.Eel>> =
  createUvCli(null, FileSystem.Eel(localEel)).mapSuccess { createUvLowLevelLocal(cwd, it) }

private fun tryExtractStderr(err: PyError): String? =
  when (err) {
    is ExecError -> {
      when (val errorReason = err.errorReason) {
        is ExecErrorReason.UnexpectedProcessTermination -> String(errorReason.stderr)
        else -> null
      }
    }
    else -> null
  }
