// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.openapi.module.Module
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.EelApi
import com.intellij.python.community.execService.ProcessOutputTransformer
import com.intellij.python.community.execService.PyProcessListener
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pixi.backend.PixiPyTool
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.intellij.python.pixi.backend.workspace.findPixiWorkspace
import com.intellij.python.pixi.backend.workspace.PixiWorkspaceOperations
import com.intellij.python.pixi.backend.workspace.parsePixiToml
import com.intellij.python.pixi.backend.workspace.PixiRuntime
import com.intellij.python.pixi.backend.workspace.createPixiRuntime
import com.intellij.python.pyproject.PyDependencyGroup
import com.intellij.python.requirements.parser.PyRequirementParser
import com.intellij.python.sdk.backend.NativePythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import com.intellij.python.sdk.backend.resolveExecutable
import com.intellij.python.sdk.backend.detectedEnvironment
import com.intellij.python.sdk.backend.toFileSystem
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.common.loadInstalledPackagesMetadata
import com.jetbrains.python.packaging.management.PyWorkspaceMember
import com.jetbrains.python.packaging.management.DependenciesExporter
import com.jetbrains.python.packaging.management.PythonManagerCliSpec
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.pip.PipPackageManagerProvider
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import com.jetbrains.python.requirements.PyDependenciesFile
import com.jetbrains.python.requirements.PyDependenciesFileProvider
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.apache.tuweni.toml.Toml
import org.apache.tuweni.toml.TomlTable
import org.jetbrains.annotations.NonNls
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

internal class PixiPackageManagerProvider : PythonPackageManagerProvider {
  override fun createPackageManager(project: Project, interpreter: PythonInterpreter): PythonPackageManager? {
    if (interpreter.ref.manager != PixiPyTool.getInstance().fusId) return null
    return PixiPackageManager(project, interpreter)
  }
}

internal class PixiPackageManager(project: Project, interpreter: PythonInterpreter) : PythonPackageManager(project, interpreter) {
  private val pipManager: PythonPackageManager = PipPackageManagerProvider().createPackageManager(project, interpreter).also {
    Disposer.register(this, it)
  }
  @Volatile
  internal var resolvedPlatformSelectors: Set<String> = emptySet()
    private set
  override val repositoryManager: PixiRepositoryManager = PixiRepositoryManager(
    project,
    pythonRepositoryManager = pipManager.repositoryManager,
    resolveInstalledPackage = { name -> withWorkspaceOperation { resolveInstalledPackage(name) } },
    loadNativeDetails = { name -> PyPiPackageRepository.buildPackageDetails(name) },
    nativeSearchContext = { withWorkspaceOperation { packageSearchContext() } },
    searchNativePackage = { name -> withWorkspaceOperation { searchNativePackage(name) } },
  ).also {
    Disposer.register(this, it)
  }
  override val installedPackagesIncludeTransitive: Boolean = true
  override val workspaceSupport = PixiWorkspaceSupport(interpreter)
  override val dependenciesExporter: DependenciesExporter = PixiDependenciesExporter(this, managerScope)
  override val dependenciesFilesRelativePaths: List<Path> = listOf(Path.of("pixi.toml"), Path.of("pyproject.toml"))

  override fun getCliSpecs(eelApi: EelApi): List<PythonManagerCliSpec> = listOf(
    PythonManagerCliSpec("pixi", { EelFileSystem(eelApi).resolveExecutable(PixiPyTool.getInstance())?.path }),
  )

  override suspend fun resolveDependencyFilesTree(): List<PyDependenciesFile> {
    val root = getRootDependenciesFile() ?: return emptyList()
    val exportFiles = readAction {
      val directory = root.virtualFile.parent ?: return@readAction emptyList()
      PIXI_ENVIRONMENT_EXPORT_FILES.mapNotNull { directory.findChild(it) }.filter { it != root.virtualFile }
    }
    return listOf(root) + exportFiles.mapNotNull { PyDependenciesFileProvider.resolve(it) }
  }

  internal suspend fun executeCommandLine(arguments: List<String>, listener: PyProcessListener): PyResult<Unit> {
    withWorkspaceOperation {
      val runtime = createRuntime(interpreter.toFileSystem()).getOr { return@withWorkspaceOperation it }
      runtime.executeWithOutput(arguments, listener)
    }.getOr { return it }
    return reloadPackages().mapSuccess { }
  }

  internal suspend fun commandPackages(): PyResult<List<PixiPackage>> = withWorkspaceOperation { listPackages() }

  override suspend fun installPackageCommand(
    installRequest: PythonPackageInstallRequest,
    options: List<String>,
    module: Module?,
    dependencyGroup: PyDependencyGroup?,
  ): PyResult<Unit> = withWorkspaceOperation { installPackage(installRequest, options, dependencyGroup) }

  private suspend fun installPackage(
    installRequest: PythonPackageInstallRequest,
    options: List<String>,
    dependencyGroup: PyDependencyGroup?,
  ): PyResult<Unit> {
    val editableLocation = when (installRequest) {
      is PythonPackageInstallRequest.ByLocation -> {
        if (installRequest.location.scheme != "file" && options.any { it == "-e" || it == "--editable" }) {
          return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.location.editable.unsupported"))
        }
        installRequest.location.scheme == "file"
      }
      is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications -> false
    }
    val installOptions = parsePixiInstallOptions(options, dependencyGroup, editableLocation).getOr { return it }
    val feature = installOptions.feature?.let { PyDependencyGroup(it) }
    return when (installRequest) {
      is PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications -> {
        if (installRequest.specifications.isEmpty()) return PyResult.success(Unit)
        val commands = linkedMapOf<List<String>, MutableList<String>>()
        for ((repository, requirement) in installRequest.specifications) {
          val pypi = !repository.isPixiNativeRepository()
          val name = if (pypi) requirement.name else {
            val rawName = requirement.presentableTextWithoutVersion
            if ('_' in rawName || '-' !in rawName) rawName
            else {
              val candidates = searchNativePackage(rawName.replace('-', '*')).getOr { return it }
              resolvePixiNativePackageName(rawName, candidates).getOr { return it }
            }
          }
          val target = dependencyTarget(name, pypi, feature).getOr { return it }
          if (pypi && target.storage == PixiDependencyStorage.PYTHON_ARRAY && installOptions.requiresPixiTable()) {
            return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.location.storage.unsupported"))
          }
          val index = if (!pypi) null else when (target.storage) {
            PixiDependencyStorage.TABLE, PixiDependencyStorage.UNDECLARED -> repository.urlForInstallation
            PixiDependencyStorage.PYTHON_ARRAY -> {
              if (repository != PyPiPackageRepository && repository.urlForInstallation != null ||
                  installOptions.arguments.any { it == "--index" || it.startsWith("--index=") }) {
                return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.index.array.unsupported", name))
              }
              null
            }
          }
          val arguments = buildList {
            add("add")
            add("--no-install")
            if (pypi) {
              add("--pypi")
              index?.let { add("--index=$it") }
            }
            addAll(target.scope.arguments)
            addAll(installOptions.arguments)
            add("--")
          }
          val specification = if (pypi) requirement.presentableText
          else target.name + requirement.versionSpecs.joinToString(",") { it.presentableText }
          commands.getOrPut(arguments) { mutableListOf() }.add(specification)
        }
        for ([arguments, specifications] in commands) {
          executePackageAdd(arguments + specifications).getOr { return it }
          installEnvironment().getOr { return it }
        }
        PyResult.success(Unit)
      }
      is PythonPackageInstallRequest.ByLocation -> {
        val location = resolvePixiPackageLocation(installRequest.location, interpreter.ownerDir) { source ->
          val indexArguments = sourceMetadataIndexArguments(installOptions).getOr { return@resolvePixiPackageLocation it }
          resolvePixiSourceName(interpreter, source, indexArguments)
        }.getOr { return it }.let { location ->
          if (interpreter !is TargetPythonInterpreter) location
          else {
            val workspace = findPixiWorkspace(interpreter.ownerDir)
                            ?: return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.workspace.missing"))
            location.relativeToWorkspace(workspace.root).getOr { return it }
          }
        }
        if (!location.isPathDependency && "--editable" in installOptions.arguments) {
          return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.location.editable.unsupported"))
        }
        val target = dependencyTarget(location.name, true, feature).getOr { return it }
        if (target.storage == PixiDependencyStorage.PYTHON_ARRAY && installOptions.requiresPixiTable()) {
          return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.location.storage.unsupported"))
        }
        val preservesDeclaration = when (target.storage) {
          PixiDependencyStorage.TABLE -> location.isPathDependency
          PixiDependencyStorage.PYTHON_ARRAY -> !location.isPathDependency || when (target.scope) {
            PixiDependencyScope.Default -> true
            is PixiDependencyScope.Feature, is PixiDependencyScope.Environment -> false
          }
          PixiDependencyStorage.UNDECLARED -> true
        }
        if (!preservesDeclaration) {
          return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.location.storage.unsupported"))
        }
        val arguments = listOf("add", "--no-install", "--pypi") + target.scope.arguments + installOptions.arguments +
                        location.arguments
        executePackageAdd(arguments).getOr { return it }
        installEnvironment()
      }
    }
  }

  private suspend fun executePackageAdd(arguments: List<String>): PyResult<Unit> {
    execute(*arguments.toTypedArray()).getOr {
      if ("--pypi" in arguments && "--no-install" in arguments &&
          requiresCondaInstallForPixiSource(it.error.message)) {
        return execute(*arguments.filterNot { argument -> argument == "--no-install" }.toTypedArray()).mapSuccess { }
      }
      return it
    }
    return PyResult.success(Unit)
  }

  override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit> =
    withWorkspaceOperation { updatePackages(specifications.toList()) }

  private suspend fun updatePackages(specifications: List<PythonRepositoryPackageSpecification>): PyResult<Unit> {
    val installed = if (specifications.any { it.repository.isPixiNativeRepository() }) listPackages().getOr { return it } else emptyList()
    val resolved = specifications.map { specification ->
      if (!specification.repository.isPixiNativeRepository()) specification
      else {
        val pkg = resolvePixiInstalledPackage(installed, specification.name).getOr { return it }
        val requirement = specification.requirement
        val text = (pkg?.name ?: requirement.presentableTextWithoutVersion) + requirement.versionSpecs.joinToString(",") { it.presentableText }
        val parsed = PyRequirementParser.fromLine(text, project)
                     ?: return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.options.invalid"))
        PythonRepositoryPackageSpecification(repositoryManager.nativeRepository, parsed)
      }
    }
    return installPackage(PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(resolved), emptyList(), dependencyGroup = null)
  }

  override suspend fun uninstallPackageCommand(
    vararg pythonPackages: String,
    workspaceMember: PyWorkspaceMember?,
    dependencyGroup: PyDependencyGroup?,
  ): PyResult<Unit> = withWorkspaceOperation { uninstallPackages(pythonPackages.toList(), dependencyGroup) }

  private suspend fun uninstallPackages(pythonPackages: List<String>, dependencyGroup: PyDependencyGroup?): PyResult<Unit> {
    if (pythonPackages.isEmpty()) return PyResult.success(Unit)
    val installed = listPackages().getOr { return it }
    val commands = linkedMapOf<Pair<List<String>, PixiDependencyScope>, MutableList<String>>()
    val removals = mutableListOf<Pair<PixiPackage, PixiDependencyTarget>>()
    for (name in pythonPackages) {
      val pkg = resolvePixiInstalledPackage(installed, name).getOr { return it } ?: continue
      val pypi = pkg.kind == "pypi"
      val target = dependencyTarget(pkg.name, pypi, dependencyGroup).getOr { return it }
      removals.add(pkg to target)
      if (target.storage == PixiDependencyStorage.UNDECLARED) continue
      val arguments = listOf("remove", "--no-install") + listOfNotNull("--pypi".takeIf { pypi }) + target.scope.arguments + "--"
      commands.getOrPut(arguments to target.scope) { mutableListOf() }.add(target.name)
    }
    for ([command, names] in commands) {
      val [arguments, scope] = command
      execute(*(arguments + names).toTypedArray()).getOr { return it }
      when (scope) {
        is PixiDependencyScope.Feature -> preservePixiFeature(project, interpreter.ownerDir, scope.name).getOr { return it }
        PixiDependencyScope.Default, is PixiDependencyScope.Environment -> Unit
      }
    }
    val remaining = if (commands.isEmpty()) emptyList() else listResolvedPackages(explicit = false).getOr { return it }
    val packagesToRemove = removals.filter { [pkg, target] ->
      when (target.storage) {
        PixiDependencyStorage.UNDECLARED -> true
        PixiDependencyStorage.TABLE, PixiDependencyStorage.PYTHON_ARRAY -> remaining.none {
          it.kind == pkg.kind && PyPackageName.from(it.name) == PyPackageName.from(pkg.name)
        }
      }
    }
    val nativePackagesToRemove = packagesToRemove.filter { [pkg, _] -> pkg.kind == "conda" }.map { [_, target] -> target.name }
    val pythonPackagesToRemove = packagesToRemove.filter { [pkg, _] -> pkg.kind == "pypi" }.map { [_, target] -> target.name }
    if (nativePackagesToRemove.isNotEmpty()) {
      val prefix = executePixiInterpreter(interpreter, Args("-c", "import sys; print(sys.prefix)"))
        .getOr { return it }.trimEnd('\r', '\n')
      val arguments = listOf("exec", "--spec", "micromamba", "--channel", "conda-forge", "--", "micromamba", "remove",
                             "--no-rc", "--no-prune-deps", "--yes", "--prefix", prefix, "--") + nativePackagesToRemove
      execute(*arguments.toTypedArray()).getOr { return it }
    }
    if (pythonPackagesToRemove.isNotEmpty()) {
      val useInstalledPip = when (interpreter) {
        is NativePythonInterpreter -> installed.any { it.name == "pip" } &&
                                     "pip" !in pythonPackagesToRemove && "pip" !in nativePackagesToRemove
        is TargetPythonInterpreter -> false
      }
      if (useInstalledPip) {
        pipManager.uninstallPackage(*pythonPackagesToRemove.toTypedArray()).getOr { return it }
      }
      else {
        executeBundledPixiPip(
          interpreter, Args("uninstall", "--yes", "--disable-pip-version-check", "--", *pythonPackagesToRemove.toTypedArray()),
        ).getOr { return it }
      }
    }
    return PyResult.success(Unit)
  }

  override suspend fun syncLockedCommand(): PyResult<Unit> = withWorkspaceOperation { installEnvironment() }

  suspend fun exportEnvironment(): PyResult<String> = withWorkspaceOperation {
    val context = packageSearchContext().getOr { return@withWorkspaceOperation it }
    exportInstalledPixiEnvironment(interpreter, interpreter.ref.envRef.value, context.channels)
  }

  private suspend fun installEnvironment(): PyResult<Unit> =
    execute("install", "--environment=${interpreter.ref.envRef.value}").mapSuccess { }

  override fun updateLockedAction(): suspend () -> PyResult<Unit> = { syncLocked().mapSuccess { } }

  override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> = withWorkspaceOperation {
    val packages = listPackages().getOr { return@withWorkspaceOperation it }
    packageSearchContext().mapSuccess { packages.map(::toPythonPackage) }
  }

  override suspend fun listDeclaredPackages(): PyResult<List<PythonPackage>> =
    withWorkspaceOperation { listPackages(explicit = true) }.mapSuccess { packages ->
      packages.map(::toPythonPackage)
    }

  private fun toPythonPackage(pkg: PixiPackage): PythonPackage = object : PythonPackage(pkg.name, pkg.version.orEmpty(), pkg.editable) {
    override val defaultRepository get() = if (pkg.kind == "conda") repositoryManager.nativeRepository else PyPiPackageRepository
  }

  override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> {
    val [packages, candidates] = withWorkspaceOperation {
      val packages = listPackages().getOr { return@withWorkspaceOperation it }
      val lockedOutput = if (hasLockFile()) listResolvedPackagesOutput(explicit = false).getOr { return@withWorkspaceOperation it } else null
      val locked = if (lockedOutput != null) parsePixiPackageList(lockedOutput).getOr { return@withWorkspaceOperation it } else emptyList()
      val nativeLocked = locked.filter { it.kind == "conda" }
        .mapNotNull { pkg -> pkg.version?.let { PixiNativePackage(pkg.name, it) } }
      val context = packageSearchContext().getOr { return@withWorkspaceOperation it }
      val output = execute("update", "--dry-run", "--json", "--environment=${interpreter.ref.envRef.value}", "--platform=${context.platform}")
        .getOr { return@withWorkspaceOperation it }
      val candidates = parsePixiCompatibleUpdates(output, interpreter.ref.envRef.value, context.platform, nativeLocked)
        .getOr { return@withWorkspaceOperation it }
      val activeArtifacts = parsePixiActiveNativeArtifacts(output, interpreter.ref.envRef.value, context.platform, lockedOutput)
        .getOr { return@withWorkspaceOperation it }
      externalNativeUpdates(packages, activeArtifacts, context)
        .mapSuccess { packages to candidates + it }
    }.getOr { return it }
    val nativePackages = packages.filter { it.kind == "conda" }
    val native = nativePackages.mapNotNull { findPixiNativePackageUpdate(it, candidates) }
    val outdated = packages.mapNotNull { findPixiPythonPackageUpdate(it, pipManager.repositoryManager) }
    return PyResult.success(native + outdated)
  }

  private suspend fun externalNativeUpdates(
    packages: List<PixiPackage>, activeArtifacts: Map<String, PixiNativeArtifact?>, context: PixiPackageSearchContext,
  ): PyResult<List<PixiNativePackage>> {
    val external = packages.filter { it.kind == "conda" && it.name !in activeArtifacts }
    if (external.isEmpty()) return PyResult.success(emptyList())
    val inventory = readPixiCondaPackagesFromInterpreter(interpreter).getOr { return it }
    val channelPriority = nativeChannelPriority().getOr { return it }
    val prefix = executePixiInterpreter(interpreter, Args("-c", "import sys; print(sys.prefix)"))
      .getOr { return it }.trimEnd('\r', '\n')
    val installedNames = packages.filter { it.kind == "conda" }.mapTo(HashSet()) { it.name }
    val candidates = mutableListOf<PixiNativePackage>()
    for ((name) in external) {
      val arguments = listOf(
        "exec", "--spec", "micromamba", "--channel", "conda-forge", "--", "micromamba", "update",
        "--no-rc", "--dry-run", "--json", "--prefix", prefix, "--override-channels", "--channel-priority=$channelPriority",
      ) + context.channels.map { "--channel=$it" } + listOf("--", name)
      val output = execute(*arguments.toTypedArray(), transformer = PIXI_EXTERNAL_UPDATE_OUTPUT_TRANSFORMER).getOr { return it }
      candidates.addAll(parsePixiExternalNativeUpdates(output, name, installedNames, inventory, activeArtifacts).getOr { return it })
    }
    return PyResult.success(candidates)
  }

  private suspend fun nativeChannelPriority(): PyResult<String> = withContext(Dispatchers.IO) {
    val workspace = findPixiWorkspace(interpreter.ownerDir)
                    ?: return@withContext PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.workspace.missing"))
    val manifest = workspace.root.resolve("pixi.toml").takeIf { it.exists() } ?: workspace.root.resolve("pyproject.toml")
    parsePixiNativeChannelPriority(manifest.readText())
  }

  override suspend fun addDependencyImpl(requirement: PyRequirement): Boolean {
    withWorkspaceOperation { addDependency(requirement) }.getOr { return false }
    return reloadPackages().successOrNull != null
  }

  private suspend fun addDependency(requirement: PyRequirement): PyResult<Unit> {
    val installed = listPackages().getOr { return it }
    val pkg = resolvePixiInstalledPackage(installed, requirement.name).getOr { return it }
    val repository = if (pkg?.kind == "conda") repositoryManager.nativeRepository else PyPiPackageRepository
    val specification = PythonRepositoryPackageSpecification(repository, requirement)
    return updatePackages(listOf(specification))
  }

  private suspend fun <T> withWorkspaceOperation(operation: suspend () -> PyResult<T>): PyResult<T> {
    val root = withContext(Dispatchers.IO) { findPixiWorkspace(interpreter.ownerDir)?.root }
               ?: return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.workspace.missing"))
    return PixiWorkspaceOperations.run(root, operation)
  }

  private suspend fun sourceMetadataIndexArguments(options: PixiInstallOptions): PyResult<List<String>> = withContext(Dispatchers.IO) {
    val workspace = findPixiWorkspace(interpreter.ownerDir)
                    ?: return@withContext PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.workspace.missing"))
    val manifest = workspace.root.resolve("pixi.toml").takeIf { it.exists() } ?: workspace.root.resolve("pyproject.toml")
    val table = parsePixiToml(manifest.readText())
                ?: return@withContext PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.manifest.invalid"))
    PyResult.success(pixiSourceMetadataIndexArguments(table, interpreter.ref.envRef.value, options.feature, options.arguments, workspace.root))
  }

  private suspend fun dependencyTarget(name: String, pypi: Boolean, group: PyDependencyGroup?): PyResult<PixiDependencyTarget> =
    withContext(Dispatchers.IO) {
      val workspace = findPixiWorkspace(interpreter.ownerDir)
                      ?: return@withContext PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.workspace.missing"))
      val manifest = workspace.root.resolve("pixi.toml").takeIf { it.exists() } ?: workspace.root.resolve("pyproject.toml")
      val table = parsePixiToml(manifest.readText())
                  ?: return@withContext PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.manifest.invalid"))
      resolvePixiDependencyTarget(table, interpreter.ref.envRef.value, name, pypi, group?.name) {
        PyRequirementParser.fromLine(it, project)?.name
      }
    }

  private suspend fun listPackages(explicit: Boolean = false): PyResult<List<PixiPackage>> {
    if (explicit) return listResolvedPackages(explicit = true)
    val native = when (val python = interpreter) {
      is NativePythonInterpreter -> {
        val prefix = python.detectedEnvironment?.pythonHomePath
                     ?: return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.add.environment.not.available"))
        readInstalledPixiCondaPackages(prefix)
      }
      is TargetPythonInterpreter -> readPixiCondaPackagesFromInterpreter(python)
    }.getOr { return it }
    val metadata = sdk.loadInstalledPackagesMetadata().getOr { return it }
    val resolved = if (hasLockFile()) listResolvedPackages(explicit = false).getOrNull().orEmpty() else emptyList()
    return PyResult.success(mergeInstalledPixiPackages(native, metadata, resolved))
  }

  private suspend fun hasLockFile(): Boolean = withContext(Dispatchers.IO) {
    findPixiWorkspace(interpreter.ownerDir)?.root?.resolve("pixi.lock")?.exists() == true
  }

  private suspend fun listResolvedPackages(explicit: Boolean): PyResult<List<PixiPackage>> {
    val output = listResolvedPackagesOutput(explicit).getOr { return it }
    return parsePixiPackageList(output)
  }

  private suspend fun listResolvedPackagesOutput(explicit: Boolean): PyResult<String> {
    val arguments = listOf("list", "--json", "--frozen", "--no-install", "--environment=${interpreter.ref.envRef.value}") +
                    listOfNotNull("--explicit".takeIf { explicit })
    return execute(*arguments.toTypedArray())
  }

  private suspend fun searchNativePackage(name: String): PyResult<List<PixiNativePackage>> {
    val context = packageSearchContext().getOr { return it }
    return searchNativePackage(name, context)
  }

  private suspend fun packageSearchContext(): PyResult<PixiPackageSearchContext> {
    val info = execute("info", "--json").getOr { return it }
    val context = parsePixiPackageSearchContext(info, interpreter.ref.envRef.value).getOr { return it }
    if (resolvedPlatformSelectors != context.targetSelectors) {
      resolvedPlatformSelectors = context.targetSelectors
      withContext(Dispatchers.EDT) {
        if (!project.isDisposed) DaemonCodeAnalyzer.getInstance(project).restart("The Pixi target platform changed")
      }
    }
    return PyResult.success(context)
  }

  private suspend fun searchNativePackage(name: String, context: PixiPackageSearchContext): PyResult<List<PixiNativePackage>> {
    val arguments = listOf("search", "--json") + context.arguments + listOf("--", name)
    val output = execute(*arguments.toTypedArray(), transformer = PIXI_SEARCH_OUTPUT_TRANSFORMER).getOr { return it }
    return parsePixiNativePackageSearch(output)
  }

  private suspend fun resolveInstalledPackage(name: String): PyResult<PixiPackage?> {
    val installed = listPackages().getOr { return it }
    return resolvePixiInstalledPackage(installed, name)
  }

  private suspend fun execute(
    vararg arguments: String,
    transformer: ProcessOutputTransformer<String> = ZeroCodeStdoutTransformer,
  ): PyResult<String> = withContext(Dispatchers.IO) {
    val runtime = createRuntime(interpreter.toFileSystem()).getOr { return@withContext it }
    runtime.execute(*arguments, processOutputTransformer = transformer)
  }

  private suspend fun <P : PathHolder> createRuntime(fileSystem: FileSystem<P>): PyResult<PixiRuntime> {
    val workspace = findPixiWorkspace(interpreter.ownerDir)
                    ?: return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.workspace.missing"))
    val executable = fileSystem.resolveExecutable(PixiPyTool.getInstance())
                     ?: return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.add.executable.not.selected"))
    return PyResult.success(createPixiRuntime(fileSystem, executable, workspace.root))
  }
}

private val PACKAGE_JSON = Json { ignoreUnknownKeys = true }
@NonNls private const val CONDA_INSTALL_REQUIRED = "installation of conda environment"
@NonNls private const val PYPI_SOURCE_SOLVE_REQUIRED = "is required to solve PyPI source dependencies"

private fun requiresCondaInstallForPixiSource(@NonNls message: String): Boolean =
  message.contains(CONDA_INSTALL_REQUIRED) && message.contains(PYPI_SOURCE_SOLVE_REQUIRED)

internal fun parsePixiPackageList(output: String): PyResult<List<PixiPackage>> = try {
  val packages = PACKAGE_JSON.decodeFromString<List<PixiPackage>>(output).map { pkg ->
    val specification = pkg.requestedSpec
    if (pkg.kind != "pypi" || specification == null) pkg
    else {
      val table = Toml.parse("dependency = $specification")
      val editable = !table.hasErrors() && (table.get("dependency") as? TomlTable)?.get("editable") == true
      pkg.copy(editable = pkg.editable || editable)
    }
  }
  PyResult.success(packages)
}
catch (_: SerializationException) {
  PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.invalid.output"))
}

@Serializable
internal data class PixiPackage(
  val name: String,
  val version: String?,
  val kind: String,
  @SerialName("is_editable") val editable: Boolean = false,
  @SerialName("requested_spec") val requestedSpec: String? = null,
)
