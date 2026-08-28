// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.impl

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.RuntimeJsonMappingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.execService.Args
import com.intellij.python.pyproject.PyDependencyGroup
import com.intellij.python.pyproject.PyDependencyGroupKind
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.ExecError
import com.jetbrains.python.errorProcessing.ExecErrorReason
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.errorProcessing.PyExecResult
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.onFailure
import com.jetbrains.python.packaging.PyPIPackageUtil
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.pip.PipParseUtils
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.uv.ScriptSyncCheckResult
import com.jetbrains.python.sdk.uv.UvCli
import com.jetbrains.python.sdk.uv.UvLowLevel
import com.jetbrains.python.sdk.uv.UvScriptEnvironment
import com.jetbrains.python.venvReader.VirtualEnvReader
import com.jetbrains.python.venvReader.tryResolvePath
import io.github.z4kn4fein.semver.Version
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString

private const val NO_METADATA_MESSAGE = "does not contain a PEP 723 metadata tag"
private const val OUTDATED_ENV_MESSAGE = "The environment is outdated"
private const val SUPPORTED_SYNC_SCHEMA_VERSION = "preview"
private val versionRegex = Regex("(\\d+\\.\\d+)\\.\\d+-.+\\s")

private class UvLowLevelImpl<P : PathHolder>(
  private val cwd: Path,
  private val venvPath: P?,
  private val uvCli: UvCli<P>,
  private val fileSystem: FileSystem<P>,
) : UvLowLevel<P> {
  override suspend fun initializeEnvironment(
    init: Boolean,
    version: Version?,
    clearExisting: Boolean,
    inheritSitePackages: Boolean,
  ): PyResult<P> {
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
      val projectName = PyPackageName.normalizeProjectName(cwd.name)
      if (projectName.isNotBlank()) {
        initArgs.add("--name")
        initArgs.add(projectName)
      }
      initArgs.add("--no-project")
      uvCli.runUv(cwd, null, true, *initArgs.toTypedArray()).getOr { return it }
    }

    val venvArgs = mutableListOf("venv")
    venvPath?.also { venvArgs += it.toString() }
    if (clearExisting) {
      venvArgs.add("--clear")
    }
    // `uv venv` accepts these, `uv init` does not, so they belong to this branch only.
    if (inheritSitePackages) {
      venvArgs.add("--system-site-packages")
      // uv defaults to `python-preference = managed`, so it would base the env on a uv-downloaded
      // interpreter whose site-packages is empty, making the inherited packages nothing at all. The
      // user asked to inherit the *system* packages, so the base has to be the system interpreter.
      venvArgs.add("--python-preference")
      venvArgs.add("system")
    }
    addPythonArg(venvArgs)
    uvCli.runUv(cwd, null, true, *venvArgs.toTypedArray()).onFailure {
      uvCli.runUv(cwd, null, true, *venvArgs.toTypedArray(), "--force").getOr { return it }
    }.getOr { return it }

    val resolvedVenvPath = venvPath?.let { fileSystem.resolvePythonBinary(it) }
    if (resolvedVenvPath != null) {
      return PyResult.success(resolvedVenvPath)
    }

    val resolvedFallback = fileSystem.resolveInWorkingDir(cwd, VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME)
                             ?.let { fileSystem.resolvePythonBinary(it) }
                           ?: return PyResult.localizedError(PyBundle.message("python.sdk.uv.failed.to.initialize.uv.environment"))

    return PyResult.success(resolvedFallback)
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
    // `uv pip list --format json` emits the same schema as `pip list --format json`,
    // including the `editable_project_location` field for editable installs.
    return PyExecResult.success(PipParseUtils.parseListResult(out))
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

  override suspend fun listPackageRequirements(name: PythonPackage): PyResult<List<PyPackageName>> {
    val out = uvCli.runUv(cwd, venvPath, false, "pip", "show", name.name)
      .getOr { return it }

    return PyExecResult.success(UvOutputParser.parseUvPackageRequirements(out))
  }

  override suspend fun listProjectStructureTree(): PyResult<String> {
    val out = uvCli.runUv(cwd, venvPath, false, "tree", "--frozen", "--no-dedupe", "--all-groups")
      .getOr { return it }

    return PyExecResult.success(out)
  }

  override suspend fun listAllPackagesTree(): PyResult<String> {
    val out = uvCli.runUv(cwd, venvPath, false, "pip", "tree")
      .getOr { return it }

    return PyExecResult.success(out)
  }

  override suspend fun installPackage(name: PythonPackageInstallRequest, options: List<String>): PyResult<Unit> {
    for (args in partitionPackagesBySource(name, options)) {
      uvCli.runUv(cwd, venvPath, true, "pip", "install", *args).getOr { return it }
    }
    return PyExecResult.success(Unit)
  }

  override suspend fun uninstallPackages(pyPackages: Array<out String>): PyResult<Unit> {
    // TODO: check if package is in dependencies and reject it
    uvCli.runUv(cwd, venvPath, true, "pip", "uninstall", *pyPackages)
      .getOr { return it }

    return PyExecResult.success(Unit)
  }

  override suspend fun addDependency(
    pyPackages: PythonPackageInstallRequest,
    options: List<String>,
    workspaceMember: PyWorkspaceMember?,
    dependencyGroup: PyDependencyGroup?,
  ): PyResult<Unit> {
    // Group flags (`--group` / `--optional`) and `--package` arrive pre-formatted inside [options]
    // when the caller goes through PythonPackageManagerUI.installPackagesRequestBackground(installOptions,…),
    // which derives them from the structured InstallOptions. Callers that don't go through the UI
    // layer can feed the raw dependencyGroup / workspaceMember params instead — leaving them null
    // is fine when the flags are already in [options].
    val args = buildList {
      add("add")
      workspaceMember?.let { add("--package"); add(it.name) }
      if (dependencyGroup != null && dependencyGroup.name != "main") {
        val flag = if (dependencyGroup.kind == PyDependencyGroupKind.OPTIONAL_DEPENDENCY) "--optional" else "--group"
        add(flag); add(dependencyGroup.name)
      }
      val editableFlag = when (pyPackages) {
        is PythonPackageInstallRequest.ByLocation -> "-e" in options && pyPackages.location.scheme == "file"
        is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications -> false
      }
      if (editableFlag) add("--editable")
      addAll(pyPackages.formatPackageName())
      addAll(options.filter { it != "-e" })
    }
    uvCli.runUv(cwd, venvPath, true, *args.toTypedArray())
      .getOr { return it }
    return PyExecResult.success(Unit)
  }

  override suspend fun removeDependencies(pyPackages: Array<out String>, workspaceMember: PyWorkspaceMember?, dependencyGroup: PyDependencyGroup?): PyResult<Unit> {
    val args = mutableListOf("remove")
    if (workspaceMember != null) {
      args.add("--package")
      args.add(workspaceMember.name)
    }
    if (dependencyGroup != null && dependencyGroup.name != "main") {
      args.add("--group")
      args.add(dependencyGroup.name)
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
    val args = Args(*constructSyncArgs(inexact).toTypedArray(), "--script").addLocalFile(scriptPath)

    uvCli.runUv(cwd, venvPath, false, args)
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

  fun PythonPackageInstallRequest.formatPackageName(): Array<String> = when (this) {
    is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications -> specifications.map { it.nameWithVersionSpecs }.toTypedArray()
    is PythonPackageInstallRequest.ByLocation -> arrayOf(location.toString())
  }

  private fun partitionPackagesBySource(installRequest: PythonPackageInstallRequest, options: List<String>): List<Array<String>> {
    if (installRequest !is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications) {
      return listOf(arrayOf(*installRequest.formatPackageName(), *options.toTypedArray()))
    }

    val (pypiSpecs, nonPypi) = installRequest.specifications.partition {
      val url = it.repository.urlForInstallation?.toString()
      url == null || url == PyPIPackageUtil.PYPI_LIST_URL
    }

    val result = mutableListOf<Array<String>>()
    if (pypiSpecs.isNotEmpty()) {
      result.add((options + pypiSpecs.map { it.nameWithVersionSpecs }).toTypedArray())
    }

    nonPypi
      .groupBy { it.repository.urlForInstallation?.toString() }
      .forEach { (url, specs) ->
        if (url == null || specs.isEmpty()) return@forEach
        result.add(buildList {
          addAll(options)
          addAll(listOf("--index-url", url))
          specs.mapTo(this) { it.nameWithVersionSpecs }
        }.toTypedArray())
      }

    return result
  }

  override suspend fun sync(python: Version?): PyResult<String> {
    val args = mutableListOf("sync", "--all-packages")
    python?.let { args.addAll(listOf("--python", "${it.major}.${it.minor}")) }
    return uvCli.runUv(cwd, venvPath, true, *args.toTypedArray())
  }

  override suspend fun lock(): PyResult<String> {
    return uvCli.runUv(cwd, venvPath, true, "lock")
  }

  override suspend fun syncScript(scriptPath: Path): PyResult<UvScriptEnvironment> {
    val args = Args("sync", "--script").addLocalFile(scriptPath).addArgs("--output-format", "json")
    val out = uvCli.runUv(cwd, venvPath, true, args)
      .getOr { return it }

    val report = try {
      jacksonObjectMapper().readTree(out)
    }
    catch (e: JacksonException) {
      return PyResult.localizedError(e.message ?: e.localizedMessage ?: e.toString())
    }

    // uv calls this schema a preview and has already revised it, so refuse an unfamiliar one rather than guess.
    val schemaVersion = report.path("schema").path("version").textValue()
    if (schemaVersion != SUPPORTED_SYNC_SCHEMA_VERSION) {
      return PyResult.localizedError(
        PyBundle.message("uv.script.sync.unsupported.schema", schemaVersion ?: "", SUPPORTED_SYNC_SCHEMA_VERSION)
      )
    }

    val environment = report.path("sync").path("environment")
    val environmentPath = environment.path("path").textValue()
    val pythonPath = environment.path("python").path("path").textValue()
    if (environmentPath.isNullOrBlank() || pythonPath.isNullOrBlank()) {
      return PyResult.localizedError(PyBundle.message("uv.script.sync.no.environment", scriptPath.pathString))
    }

    return PyResult.success(UvScriptEnvironment(environmentPath, pythonPath))
  }
}

/**
 * Arguments of the `uv sync --check` used to ask whether an environment is up to date. Lifted out of the
 * implementation so that the flags it builds can be asserted directly.
 */
internal fun constructSyncArgs(inexact: Boolean): MutableList<String> {
  val args = mutableListOf("sync", "--check", "--all-packages")

  if (inexact) {
    args += "--inexact"
  }

  return args
}

internal fun createUvLowLevelLocal(cwd: Path, uvCli: UvCli<PathHolder.Eel>): UvLowLevel<PathHolder.Eel> =
  createUvLowLevel(cwd, uvCli, EelFileSystem(localEel), null)

internal fun <P : PathHolder> createUvLowLevel(cwd: Path, uvCli: UvCli<P>, fileSystem: FileSystem<P>, venvPath: P?): UvLowLevel<P> =
  UvLowLevelImpl(cwd, venvPath, uvCli, fileSystem)

internal suspend fun createUvLowLevelLocal(cwd: Path): PyResult<UvLowLevel<PathHolder.Eel>> =
  validateAndCreateUvCli(null, EelFileSystem(localEel)).mapSuccess { createUvLowLevelLocal(cwd, it) }

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
