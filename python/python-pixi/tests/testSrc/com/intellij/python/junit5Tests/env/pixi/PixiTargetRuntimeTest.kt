// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.pixi

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.Platform
import com.intellij.execution.target.BaseTargetEnvironmentRequest
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.execution.target.TargetPlatform
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.TargetedCommandLine
import com.intellij.execution.target.value.constantExplicit
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.SystemInfo
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.community.execService.impl.TargetEnvironmentRequestHandler
import com.intellij.python.junit5Tests.framework.LeakedProcessReporterExtension
import com.intellij.python.pixi.backend.workspace.createPixiRuntime
import com.intellij.python.pixi.backend.packaging.resolvePixiPackageLocation
import com.intellij.python.pixi.backend.packaging.relativeToWorkspace
import com.intellij.python.pixi.backend.packaging.resolvePixiSourceName
import com.intellij.python.pixi.backend.packaging.executeBundledPixiPip
import com.intellij.python.pixi.backend.packaging.readPixiCondaPackagesFromInterpreter
import com.intellij.python.pixi.backend.sdk.PixiSdkFlavor
import com.intellij.python.pixi.test.env.PixiPyEnvironment
import com.intellij.python.pixi.test.env.pixiEnvironment
import com.intellij.python.test.env.core.PyEnvironmentFactory
import com.intellij.python.test.env.junit5.PythonFactoryExtension
import com.intellij.python.sdk.backend.pythonInterpreterResultAsync
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.io.Compressor
import com.sun.net.httpserver.HttpServer
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isSuccess
import com.jetbrains.python.run.target.HelpersAwareTargetEnvironmentRequest
import com.jetbrains.python.run.target.PythonHelpersMappings
import com.jetbrains.python.run.target.PathPythonHelpersMapping
import com.jetbrains.python.run.target.getPythonHelpers
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.add.v2.FileSystemFactory
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.params.provider.EnumSource
import org.apache.tuweni.toml.Toml
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Icon
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

@TestApplication
@Subsystems.Interpreters
@Layers.Functional
@ExtendWith(PythonFactoryExtension::class, LeakedProcessReporterExtension::class)
internal class PixiTargetRuntimeTest {
  private val disposable = disposableFixture()

  @ParameterizedTest
  @EnumSource(Platform::class)
  fun `target bundled pip uses an existing mapped directory on each platform`(
    platform: Platform, @TempDir temporaryDirectory: Path, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    checkTargetSourceMetadata(archive = false, temporaryDirectory, factory, useFindLinks = false,
                              parentMapping = true, bundledPip = true, platform = platform)
  }

  @ParameterizedTest
  @EnumSource(Platform::class)
  fun `target source metadata uploads its directory on each platform`(
    platform: Platform, @TempDir temporaryDirectory: Path, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    checkTargetSourceMetadata(archive = false, temporaryDirectory, factory, useFindLinks = false,
                              parentMapping = true, platform = platform)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `target bundled pip leaves mapped workspaces out of uploads`(
    unrelatedMapping: Boolean, @TempDir temporaryDirectory: Path, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    checkTargetSourceMetadata(archive = false, temporaryDirectory, factory, useFindLinks = false,
                              parentMapping = true, unrelatedMapping = unrelatedMapping, bundledPip = true)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `target native inventory leaves mapped workspaces out of uploads`(
    unrelatedMapping: Boolean, @TempDir temporaryDirectory: Path, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    checkTargetSourceMetadata(archive = false, temporaryDirectory, factory, useFindLinks = false,
                              parentMapping = true, unrelatedMapping = unrelatedMapping, nativeInventory = true)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `target source metadata leaves the local prefix out of uploads`(
    archive: Boolean, @TempDir temporaryDirectory: Path, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    checkTargetSourceMetadata(archive, temporaryDirectory, factory, useFindLinks = false)
  }

  @Test
  fun `target source metadata maps local links without uploading the local prefix`(
    @TempDir temporaryDirectory: Path, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    checkTargetSourceMetadata(archive = false, temporaryDirectory, factory, useFindLinks = true)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `target source metadata uses a parent upload volume without uploading the local prefix`(
    archive: Boolean, @TempDir temporaryDirectory: Path, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    checkTargetSourceMetadata(archive, temporaryDirectory, factory, useFindLinks = false, parentMapping = true)
  }

  @Test
  fun `target source metadata rejects unrelated upload volumes before uploading the local prefix`(
    @TempDir temporaryDirectory: Path, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    checkTargetSourceMetadata(archive = false, temporaryDirectory, factory, useFindLinks = false,
                              parentMapping = true, unrelatedMapping = true)
  }

  @Test
  fun `target source metadata reads a remote archive without uploading the mapped workspace`(
    @TempDir temporaryDirectory: Path, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    checkTargetSourceMetadata(archive = true, temporaryDirectory, factory, useFindLinks = false,
                              parentMapping = true, remoteArchive = true)
  }

  private suspend fun checkTargetSourceMetadata(
    archive: Boolean, temporaryDirectory: Path, factory: PyEnvironmentFactory, useFindLinks: Boolean, parentMapping: Boolean = false,
    unrelatedMapping: Boolean = false,
    remoteArchive: Boolean = false,
    bundledPip: Boolean = false,
    nativeInventory: Boolean = false,
    platform: Platform = Platform.UNIX,
  ) {
    val environment = checkNotNull(factory.createEnvironment(pixiEnvironment(PIXI_TEST_VERSION) {
      pythonVersion = pythonVersion("3.13")
    }).unwrap<PixiPyEnvironment>())
    val localRoot = temporaryDirectory.resolve("IDE metadata project").createDirectories()
    val remoteRoot = temporaryDirectory.resolve("target metadata project").createDirectories()
    localRoot.resolve(".pixi/envs/default").createDirectories().resolve("host-prefix.txt").writeText("local prefix")
    remoteRoot.resolve(".pixi/envs/default").createDirectories().resolve("target-prefix.txt").writeText("target prefix")
    val files = mapOf(
      "pyproject.toml" to if (useFindLinks) """
        [build-system]
        requires = ['pixi-target-metadata-backend==1.0']
        build-backend = 'metadata_backend'
      """.trimIndent() else """
        [build-system]
        requires = []
        build-backend = 'metadata_backend'
        backend-path = ['.']
      """.trimIndent(),
      ".git/metadata.txt" to "version metadata",
      "metadata_backend.py" to """
        from pathlib import Path

        def get_requires_for_build_wheel(config_settings=None):
            return []

        def prepare_metadata_for_build_wheel(metadata_directory, config_settings=None):
            assert (Path.cwd() / '.git/metadata.txt').read_text() == 'version metadata'
            directory = Path(metadata_directory) / 'pixi_target_metadata_probe-1.0.dist-info'
            directory.mkdir()
            (directory / 'METADATA').write_text('Metadata-Version: 2.1\nName: pixi-target-metadata-probe\nVersion: 1.0\n')
            return directory.name
      """.trimIndent(),
    )
    val sourceRoot = if (parentMapping) localRoot.resolve("child source").createDirectories() else localRoot
    sourceRoot.resolve(".pixi/envs/default").createDirectories().resolve("source-prefix.txt").writeText("source prefix")
    val source = if (archive) {
      sourceRoot.resolve("unknown-source.tar.gz").also { path ->
        Compressor.Tar(path, Compressor.Tar.Compression.GZIP).use { tar ->
          for ([name, text] in files) tar.addFile("source/$name", text.byteInputStream(), 0)
        }
      }
    }
    else sourceRoot.also { root ->
      for ([name, text] in files) root.resolve(name).also { it.parent.createDirectories() }.writeText(text)
    }
    val config = StagedTargetConfiguration(localRoot, remoteRoot, environment.pythonPath, parentMapping, unrelatedMapping,
                                           platform = platform)
    val targetTypes = TargetEnvironmentType.EXTENSION_NAME
    ExtensionTestUtil.maskExtensions(targetTypes, targetTypes.extensionList + StagedTargetType(config), disposable.get())
    val handlers = ExtensionPointName.create<TargetEnvironmentRequestHandler<*>>("Pythonid.execService.targetEnvironmentRequestHandler")
    ExtensionTestUtil.maskExtensions(handlers, handlers.extensionList + StagedTargetHandler(), disposable.get())
    val data = PyTargetAwareAdditionalData(PyFlavorAndData(PyFlavorData.Empty, PixiSdkFlavor), localRoot, config)
    val sdk = ProjectJdkTable.getInstance().createSdk("Pixi metadata target", PythonSdkType.getInstance())
    edtWriteAction {
      sdk.sdkModificator.apply {
        homePath = environment.pythonPath.toString()
        sdkAdditionalData = data
        commitChanges()
      }
    }
    val interpreter = sdk.pythonInterpreterResultAsync().getOrThrow()
    assertThat(interpreter).isInstanceOf(TargetPythonInterpreter::class.java)
    val links = temporaryDirectory.resolve("private target wheels").createDirectories()
    val arguments = if (useFindLinks) {
      links.resolve(".pixi/envs/default").createDirectories().resolve("private-prefix.txt").writeText("private prefix")
      links.resolve("unrelated directory").createDirectories().resolve("private.txt").writeText("unrelated file")
      val metadata = "pixi_target_metadata_backend-1.0.dist-info"
      val backendFiles = mapOf(
        "metadata_backend.py" to files.getValue("metadata_backend.py"),
        "$metadata/METADATA" to "Metadata-Version: 2.1\nName: pixi-target-metadata-backend\nVersion: 1.0\n",
        "$metadata/WHEEL" to "Wheel-Version: 1.0\nRoot-Is-Purelib: true\nTag: py3-none-any\n",
      )
      Compressor.Zip(links.resolve("pixi_target_metadata_backend-1.0-py3-none-any.whl")).use { zip ->
        for ([name, text] in backendFiles) zip.addFile(name, text.byteInputStream(), 0)
        zip.addFile("$metadata/RECORD", (backendFiles.keys + "$metadata/RECORD").joinToString("\n", postfix = "\n") {
          "$it,,"
        }.byteInputStream(), 0)
      }
      listOf("--no-index", "--find-links", links.toUri().toASCIIString())
    }
    else emptyList()

    config.trace.clear()
    val requests = AtomicInteger()
    val result = if (nativeInventory) {
      readPixiCondaPackagesFromInterpreter(interpreter).mapSuccess { packages ->
        assertThat(packages.map { it.name }).contains("python")
        "pixi-target-metadata-probe"
      }
    }
    else if (bundledPip) {
      executeBundledPixiPip(interpreter, Args(), "print('pixi-target-metadata-probe')").mapSuccess { it.trim() }
    }
    else if (remoteArchive) {
      val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
      server.createContext("/source.tar.gz") { exchange ->
        requests.incrementAndGet()
        val bytes = source.readBytes()
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
      }
      server.start()
      try {
        resolvePixiSourceName(interpreter, URI("http://127.0.0.1:${server.address.port}/source.tar.gz"), arguments)
      }
      finally {
        server.stop(0)
      }
    }
    else resolvePixiSourceName(interpreter, source.toUri(), arguments)
    if (unrelatedMapping) {
      assertThat(result.isSuccess).isFalse()
      assertThat(result.errorOrNull?.message).contains("Remove unrelated mappings.")
      assertThat(config.uploads).isEmpty()
      assertThat(config.commands).isEmpty()
      assertThat(remoteRoot.resolve(".pixi/envs/default/host-prefix.txt")).doesNotExist()
      assertThat(remoteRoot.resolve(".pixi/envs/default/target-prefix.txt").readText()).isEqualTo("target prefix")
      return
    }
    assertThat(result.isSuccess).describedAs(result.errorOrNull?.message).isTrue()
    val name = result.getOrThrow()

    assertThat(name).isEqualTo("pixi-target-metadata-probe")
    assertThat(config.commands).isNotEmpty()
    if (remoteArchive || bundledPip || nativeInventory) {
      if (remoteArchive) assertThat(requests.get()).isPositive()
      assertThat(config.uploads).isEmpty()
    }
    else assertThat(config.uploadSources.map { it.first }).contains(localRoot)
    if (useFindLinks) {
      val command = config.commands.last()
      assertThat(command).doesNotContain(links.toUri().toASCIIString())
      val uploaded = config.uploadSources.filter { it.first != localRoot }
      assertThat(uploaded).isNotEmpty()
      for ([local, remote] in uploaded) {
        assertThat(local).doesNotExist()
        assertThat(remote.resolve(".pixi/envs/default/private-prefix.txt")).doesNotExist()
        assertThat(remote.resolve("unrelated directory/private.txt")).doesNotExist()
      }
    }
    else assertThat(config.uploads).doesNotContain(".", ".pixi")
    assertThat(remoteRoot.resolve(".pixi/envs/default/host-prefix.txt")).doesNotExist()
    assertThat(remoteRoot.resolve(".pixi/envs/default/target-prefix.txt").readText()).isEqualTo("target prefix")
    if (parentMapping) assertThat(remoteRoot.resolve("child source/.pixi/envs/default/source-prefix.txt")).doesNotExist()
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `a target source install records a portable path and imports on the target`(
    wheel: Boolean, @TempDir temporaryDirectory: Path, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val environment = checkNotNull(factory.createEnvironment(pixiEnvironment(PIXI_TEST_VERSION) {
      pythonVersion = pythonVersion("3.13")
    }).unwrap<PixiPyEnvironment>())
    val localRoot = temporaryDirectory.resolve("IDE source project").createDirectories()
    val remoteRoot = temporaryDirectory.resolve("target source project").createDirectories()
    val directory = localRoot.resolve("source with spaces").createDirectories()
    val source = if (wheel) {
      directory.resolve("target_source_probe-1.0-py3-none-any.whl").also { path ->
        val metadata = "target_source_probe-1.0.dist-info"
        val files = mapOf(
          "target_source_probe.py" to "VALUE = 42\n",
          "$metadata/METADATA" to "Metadata-Version: 2.1\nName: target-source-probe\nVersion: 1.0\n",
          "$metadata/WHEEL" to "Wheel-Version: 1.0\nRoot-Is-Purelib: true\nTag: py3-none-any\n",
        )
        Compressor.Zip(path).use { archive ->
          for ([name, text] in files) archive.addFile(name, text.byteInputStream(), 0)
          archive.addFile("$metadata/RECORD", (files.keys + "$metadata/RECORD").joinToString("\n", postfix = "\n") {
            "$it,,"
          }.byteInputStream(), 0)
        }
      }
    }
    else directory.also {
      it.resolve("pyproject.toml").writeText("""
        [project]
        name = 'target-source-probe'
        version = '1.0'
        [build-system]
        requires = ['setuptools']
        build-backend = 'setuptools.build_meta'
      """.trimIndent())
      it.resolve("target_source_probe.py").writeText("VALUE = 42\n")
    }
    localRoot.resolve("pixi.toml").writeText("""
      [workspace]
      name = 'target-source-install'
      channels = ['conda-forge']
      platforms = ['linux-64', 'linux-aarch64', 'osx-arm64', 'osx-64', 'win-64']
      [dependencies]
      python = '3.13.*'
    """.trimIndent() + "\n")
    val config = StagedTargetConfiguration(localRoot, remoteRoot, environment.pythonPath)
    val targetTypes = TargetEnvironmentType.EXTENSION_NAME
    ExtensionTestUtil.maskExtensions(targetTypes, targetTypes.extensionList + StagedTargetType(config), disposable.get())
    val handlers = ExtensionPointName.create<TargetEnvironmentRequestHandler<*>>("Pythonid.execService.targetEnvironmentRequestHandler")
    ExtensionTestUtil.maskExtensions(handlers, handlers.extensionList + StagedTargetHandler(), disposable.get())
    val runtime = createPixiRuntime(FileSystemFactory.getInstance().create(config),
                                    PathHolder.Target(environment.pixiExecutable.toString()), localRoot)
    val location = resolvePixiPackageLocation(source.toUri(), localRoot).getOrThrow().relativeToWorkspace(localRoot).getOrThrow()

    runtime.execute("add", "--pypi", *location.arguments.toTypedArray(), processOutputTransformer = ZeroCodeStdoutTransformer).getOrThrow()

    val manifest = localRoot.resolve("pixi.toml").readText()
    assertThat(remoteRoot.resolve("pixi.toml").readText()).isEqualTo(manifest)
    assertThat(manifest).doesNotContain(localRoot.toString(), remoteRoot.toString())
    val dependencies = checkNotNull(Toml.parse(manifest).getTable("pypi-dependencies"))
    assertThat(dependencies.keySet()).containsExactly("target-source-probe")
    val path = checkNotNull(dependencies.getTable(listOf("target-source-probe"))?.getString("path"))
    assertThat(Path.of(path).isAbsolute).isFalse()
    assertThat(localRoot.resolve(path).normalize()).isEqualTo(source)
    val binary = remoteRoot.resolve(".pixi/envs/default").resolve(if (SystemInfo.isWindows) "python.exe" else "bin/python")
    assertThat(ExecService().execGetStdout(binary, Args("-c", "import target_source_probe; print(target_source_probe.VALUE)"))
                 .getOrThrow().trim()).isEqualTo("42")
    runtime.execute("install", processOutputTransformer = ZeroCodeStdoutTransformer).getOrThrow()
    assertThat(ExecService().execGetStdout(binary, Args("-c", "import target_source_probe; print(target_source_probe.VALUE)"))
                 .getOrThrow().trim()).isEqualTo("42")
  }

  @Test
  fun `target mutations preserve both manifests and keep the target prefix`(
    @TempDir temporaryDirectory: Path, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val environment = checkNotNull(factory.createEnvironment(pixiEnvironment(PIXI_TEST_VERSION) {
      pythonVersion = pythonVersion("3.13")
    }).unwrap<PixiPyEnvironment>())
    val localRoot = temporaryDirectory.resolve("IDE project").createDirectories()
    val remoteRoot = temporaryDirectory.resolve("target O'Brien project").createDirectories()
    localRoot.resolve("pixi.toml").writeText("""
      [workspace]
      name = 'target-runtime'
      channels = []
      platforms = ['linux-64', 'linux-aarch64', 'osx-arm64', 'osx-64', 'win-64']
    """.trimIndent() + "\n")
    localRoot.resolve(".pixi/envs/default").createDirectories().resolve("host-only.txt").writeText("host prefix")
    remoteRoot.resolve(".pixi/envs/default").createDirectories().resolve("target-only.txt").writeText("target prefix")
    localRoot.resolve("src").createDirectories().resolve("probe.txt").writeText("project input")
    val config = StagedTargetConfiguration(localRoot, remoteRoot, environment.pythonPath)
    val targetTypes = TargetEnvironmentType.EXTENSION_NAME
    ExtensionTestUtil.maskExtensions(targetTypes, targetTypes.extensionList + StagedTargetType(config), disposable.get())
    val handlers = ExtensionPointName.create<TargetEnvironmentRequestHandler<*>>("Pythonid.execService.targetEnvironmentRequestHandler")
    ExtensionTestUtil.maskExtensions(handlers, handlers.extensionList + StagedTargetHandler(), disposable.get())
    val fileSystem = FileSystemFactory.getInstance().create(config)
    val runtime = createPixiRuntime(fileSystem, PathHolder.Target(environment.pixiExecutable.toString()), localRoot)

    runtime.execute("workspace", "environment", "add", "docs", processOutputTransformer = ZeroCodeStdoutTransformer).getOrThrow()

    val first = localRoot.resolve("pixi.toml").readText()
    assertThat(first).contains("docs")
    assertThat(remoteRoot.resolve("pixi.toml").readText()).isEqualTo(first)
    runtime.execute("workspace", "environment", "add", "lint", processOutputTransformer = ZeroCodeStdoutTransformer).getOrThrow()

    val latest = localRoot.resolve("pixi.toml").readText()
    assertThat(latest).contains("docs", "lint")
    assertThat(remoteRoot.resolve("pixi.toml").readText()).isEqualTo(latest)
    assertThat(remoteRoot.resolve("src/probe.txt").readText()).isEqualTo("project input")
    assertThat(remoteRoot.resolve(".pixi/envs/default/host-only.txt")).doesNotExist()
    assertThat(remoteRoot.resolve(".pixi/envs/default/target-only.txt").readText()).isEqualTo("target prefix")
    assertThat(config.uploads).contains("pixi.toml", "src").doesNotContain(".", ".pixi")
    assertThat(config.downloads.count { it == "pixi.toml" }).isEqualTo(2)
    assertThat(Path.of(runtime.resolveTargetPath(".pixi/envs/docs").getOrThrow()))
      .isEqualTo(remoteRoot.resolve(".pixi/envs/docs"))
  }

  internal class StagedTargetConfiguration(
    val localRoot: Path, val remoteRoot: Path, val python: Path = remoteRoot.resolve("python"), val parentMapping: Boolean = false,
    val unrelatedMapping: Boolean = false,
    val trace: StagedTargetTrace = StagedTargetTrace(),
    val platform: Platform = Platform.UNIX,
  ) :
    TargetEnvironmentConfiguration("pixi-staged-test"), PersistentStateComponent<Element> {
    val uploads get() = trace.uploads
    val downloads get() = trace.downloads
    val uploadSources get() = trace.uploadSources
    val commands get() = trace.commands
    override var projectRootOnTarget: String = remoteRoot.toString()
    override fun getState(): Element = Element("target")
    override fun loadState(state: Element) = Unit
  }

  internal class StagedTargetTrace {
    val uploads = mutableListOf<String>()
    val downloads = mutableListOf<String>()
    val uploadSources = mutableListOf<Pair<Path, Path>>()
    val commands = mutableListOf<List<String>>()

    fun clear() {
      uploads.clear()
      downloads.clear()
      uploadSources.clear()
      commands.clear()
    }
  }

  internal class StagedTargetType(private val template: StagedTargetConfiguration) :
    TargetEnvironmentType<StagedTargetConfiguration>("pixi-staged-test") {
    override val displayName: String = "The staged test target"
    override val icon: Icon = EmptyIcon.ICON_16
    override fun createSerializer(config: StagedTargetConfiguration): PersistentStateComponent<*> = config
    override fun createDefaultConfig(): StagedTargetConfiguration =
      StagedTargetConfiguration(template.localRoot, template.remoteRoot, template.python,
                                template.parentMapping, template.unrelatedMapping, template.trace, template.platform)
    override fun duplicateConfig(config: StagedTargetConfiguration): StagedTargetConfiguration =
      StagedTargetConfiguration(config.localRoot, config.remoteRoot, config.python,
                                config.parentMapping, config.unrelatedMapping, config.trace, config.platform)

    override fun createEnvironmentRequest(project: Project?, config: StagedTargetConfiguration): TargetEnvironmentRequest =
      StagedTargetRequest(config)

    override fun createConfigurable(
      project: Project,
      config: StagedTargetConfiguration,
      defaultLanguage: LanguageRuntimeType<*>?,
      parentConfigurable: Configurable?,
    ): Configurable = error("The test target has no settings.")
  }

  private class StagedTargetHandler : TargetEnvironmentRequestHandler<StagedTargetRequest>(StagedTargetRequest::class.java) {
    override suspend fun mapUploadRootsImpl(request: StagedTargetRequest, localDirs: Set<Path>): UploadInfo = UploadInfo(
      object : HelpersAwareTargetEnvironmentRequest {
        override val targetEnvironmentRequest: TargetEnvironmentRequest = request
        override fun preparePyCharmHelpers(): PythonHelpersMappings = PythonHelpersMappings(
          getPythonHelpers().map { PathPythonHelpersMapping(it, constantExplicit(it.toString())) },
        )
      },
      buildMap {
        put(request.configuration.localRoot, request.configuration.remoteRoot.toString())
        for ([index, directory] in localDirs.filter { !it.startsWith(request.configuration.localRoot) }.withIndex()) {
          put(directory, request.configuration.remoteRoot.resolve("metadata-links-$index").toString())
        }
      },
    )
  }

  private class StagedTargetRequest(override val configuration: StagedTargetConfiguration) : BaseTargetEnvironmentRequest() {
    override val targetPlatform: TargetPlatform = TargetPlatform(configuration.platform)

    init {
      if (configuration.parentMapping) {
        uploadVolumes.add(TargetEnvironment.UploadRoot(configuration.localRoot,
                                                       TargetEnvironment.TargetPath.Persistent(configuration.remoteRoot.toString())))
      }
      if (configuration.unrelatedMapping) {
        val unrelated = configuration.localRoot.parent.resolve("unrelated mapping").createDirectories()
        unrelated.resolve("private.txt").writeText("private file")
        uploadVolumes.add(TargetEnvironment.UploadRoot(unrelated,
                                                       TargetEnvironment.TargetPath.Persistent(configuration.remoteRoot.resolve("unrelated").toString())))
      }
    }

    override fun prepareEnvironment(progressIndicator: TargetProgressIndicator): TargetEnvironment = StagedTargetEnvironment(this).also {
      environmentPrepared(it, progressIndicator)
    }
  }

  private class StagedTargetEnvironment(private val stagedRequest: StagedTargetRequest) : TargetEnvironment(stagedRequest) {
    override val targetPlatform: TargetPlatform = stagedRequest.targetPlatform
    override val targetPortBindings: Map<TargetPortBinding, com.intellij.execution.target.ResolvedPortBinding> = emptyMap()
    override val uploadVolumes: Map<UploadRoot, UploadableVolume> = stagedRequest.uploadVolumes.associateWith { root ->
      val remoteRoot = (root.targetRootPath as TargetPath.Persistent).absolutePath
      object : UploadableVolume {
        override val localRoot: Path = root.localRootPath
        override val targetRoot: String = remoteRoot
        override fun resolveTargetPath(relativePath: String): String = Path.of(targetRoot).resolve(relativePath).toString()
        override fun upload(relativePath: String, targetProgressIndicator: TargetProgressIndicator) {
          stagedRequest.configuration.uploads.add(relativePath)
          stagedRequest.configuration.uploadSources.add(localRoot to Path.of(targetRoot))
          copyTree(localRoot.resolve(relativePath), Path.of(targetRoot).resolve(relativePath))
        }
      }
    }
    override val downloadVolumes: Map<DownloadRoot, DownloadableVolume> = stagedRequest.downloadVolumes.associateWith { root ->
      val remoteRoot = (root.targetRootPath as TargetPath.Persistent).absolutePath
      object : DownloadableVolume {
        override val localRoot: Path = checkNotNull(root.localRootPath)
        override val targetRoot: String = remoteRoot
        override fun resolveTargetPath(relativePath: String): String = Path.of(targetRoot).resolve(relativePath).toString()
        override fun download(relativePath: String, progressIndicator: ProgressIndicator) {
          stagedRequest.configuration.downloads.add(relativePath)
          copyTree(Path.of(targetRoot).resolve(relativePath), localRoot.resolve(relativePath))
        }
      }
    }

    override fun createProcess(commandLine: TargetedCommandLine, indicator: ProgressIndicator): Process {
      val arguments = commandLine.collectCommandsSynchronously()
      stagedRequest.configuration.commands.add(arguments)
      fun onHost(path: String): String = when (stagedRequest.configuration.platform) {
        Platform.UNIX -> path
        Platform.WINDOWS -> if (SystemInfo.isWindows) path else {
          val converted = path.replace('\\', '/')
          val roots = listOf(stagedRequest.configuration.remoteRoot) + getPythonHelpers()
          if (roots.any { converted.startsWith(it.toString()) }) converted else path
        }
      }
      val hostArguments = arguments.map(::onHost)
      val commands = when (hostArguments.first()) {
        "/bin/mkdir" -> listOf(
          stagedRequest.configuration.python.toString(), "-c",
          "import pathlib, sys; pathlib.Path(sys.argv[1]).mkdir(parents=True, exist_ok=True)", hostArguments.last(),
        )
        "test" -> listOf(
          stagedRequest.configuration.python.toString(), "-c",
          "import pathlib, sys; sys.exit(0 if pathlib.Path(sys.argv[1]).is_file() else 1)", hostArguments.last(),
        )
        else -> hostArguments
      }
      val command = GeneralCommandLine(commands).withEnvironment(commandLine.environmentVariables)
      commandLine.workingDirectory?.let { command.withWorkDirectory(onHost(it)) }
      return command.createProcess()
    }

    override fun shutdown() = Unit
  }

  companion object {
    private fun copyTree(source: Path, destination: Path) {
      check(source.exists())
      if (source.isDirectory()) {
        Files.walk(source).use { entries ->
          entries.forEach { entry ->
            val copy = destination.resolve(source.relativize(entry))
            if (entry.isDirectory()) copy.createDirectories()
            else {
              checkNotNull(copy.parent).createDirectories()
              Files.copy(entry, copy, REPLACE_EXISTING)
            }
          }
        }
      }
      else {
        checkNotNull(destination.parent).createDirectories()
        Files.copy(source, destination, REPLACE_EXISTING)
      }
    }
  }
}
