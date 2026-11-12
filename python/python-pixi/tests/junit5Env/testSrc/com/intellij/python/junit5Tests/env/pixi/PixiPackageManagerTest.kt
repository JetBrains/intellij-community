// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.pixi

import com.intellij.idea.TestFor
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.Executor
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider
import com.intellij.ide.actions.runAnything.activity.RunAnythingCommandLineProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.community.impl.conda.environmentYml.format.CondaEnvironmentYmlParser
import com.intellij.python.junit5Tests.framework.LeakedProcessReporterExtension
import com.intellij.python.junit5Tests.framework.errorSinkFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pixi.backend.PixiPyTool
import com.intellij.python.pixi.backend.packaging.PixiPackageManager
import com.intellij.python.pixi.backend.packaging.PixiNativeRepository
import com.intellij.python.pixi.backend.packaging.readPixiCondaPackagesFromInterpreter
import com.intellij.python.pixi.test.env.PixiPyEnvironment
import com.intellij.python.pixi.test.env.pixiEnvironment
import com.intellij.python.pyproject.model.api.InterpreterConfigurationResult
import com.intellij.python.pyproject.model.api.configureSdkIfNeeded
import com.intellij.python.pyproject.PyDependencyGroup
import com.intellij.python.pyproject.model.spi.ProjectName
import com.intellij.python.pyproject.model.spi.PyProjectManager
import com.intellij.python.pyproject.model.evolution.getInterpreter
import com.intellij.python.pytools.backend.getCustomExecutablePath
import com.intellij.python.pytools.backend.setCustomExecutablePath
import com.intellij.python.requirements.pyRequirementVersionSpec
import com.intellij.python.requirements.parser.PyRequirementParser
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.detectedEnvironment
import com.intellij.python.sdk.common.PyEnvRef
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.python.test.env.core.PyEnvironmentFactory
import com.intellij.python.test.env.junit5.PythonFactoryExtension
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.io.Compressor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isSuccess
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.conda.CondaPackageRepository
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.management.ui.installPackageBackground
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.apache.tuweni.toml.Toml
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import java.security.MessageDigest
import kotlin.io.path.readText
import kotlin.io.path.readBytes
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.io.path.moveTo
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@TestApplication
@Subsystems.PackagingRequirements
@Layers.Functional
@TestFor(issues = ["PY-91735"])
@ExtendWith(PythonFactoryExtension::class, LeakedProcessReporterExtension::class)
internal class PixiPackageManagerTest(private val environmentFactory: PyEnvironmentFactory) {
  private val rootFixture = tempPathFixture()
  private val projectFixture = projectFixture(rootFixture)
  private val pyProjectFixture = projectFixture.pyProjectFixture(rootFixture)
  private val errorSink = errorSinkFixture()
  private val runContentDisposable = testFixture { context ->
    projectFixture.init()
    val disposable = Disposer.newCheckedDisposable(context.uniqueId)
    initialized(disposable) {
      withContext(Dispatchers.EDT) { Disposer.dispose(disposable) }
    }
  }

  @Test
  fun `Run Anything executes Pixi in its workspace and reloads packages`(): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace("pixi.toml", environmentFactory, defaultEnvironmentDefinition = "[]", includeAnalysisEnvironment = false) { _, manager ->
      val project = projectFixture.get()
      val provider = RunAnythingProvider.EP_NAME.extensionList.filterIsInstance<RunAnythingCommandLineProvider>()
        .single { it.helpCommand == "pixi" }
      val context = SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, project)
      val shown = CompletableDeferred<RunContentDescriptor>()
      val disposable = runContentDisposable.get()
      val contents = RunContentManager.getInstance(project)
      project.replaceService(RunContentManager::class.java, object : RunContentManager by contents {
        override fun getContentDescriptorToolWindowId(environment: ExecutionEnvironment): String? =
          contents.getContentDescriptorToolWindowId(environment)

        override fun showRunContent(executor: Executor, descriptor: RunContentDescriptor) {
          Disposer.register(disposable, descriptor)
          shown.complete(descriptor)
        }
      }, disposable)

      provider.execute(context, "pixi add --pypi six==1.16.0 testpath==0.6.0")

      val descriptor = withTimeout(30.seconds) { shown.await() }
      waitUntilAssertSucceeds("The Pixi command must finish and refresh installed packages.") {
        assertThat(descriptor.processHandler?.isProcessTerminated).isTrue()
        assertThat(descriptor.processHandler?.exitCode).isZero()
        assertThat(declaredPackages("pixi.toml")).contains("six")
        assertThat(manager.listInstalledPackagesSnapshot().filter { it.name == "six" })
          .describedAs("Installed packages: %s", manager.listInstalledPackagesSnapshot())
          .singleElement().extracting { it.version }.isEqualTo("1.16.0")
        assertThat((descriptor.executionConsole as ConsoleView).contentSize).isPositive()
      }
      assertThat(provider.getValues(context, "pixi remove --pypi si")).contains("pixi remove --pypi six")
      assertThat(provider.getValues(context, "pixi remove si")).doesNotContain("pixi remove six")
      assertThat(provider.getValues(context, "pixi remove python_")).contains("pixi remove python_abi")
      assertThat(provider.getValues(context, "pixi remove --pypi pyt")).doesNotContain("pixi remove --pypi python")
      assertThat(provider.getValues(context, "pixi remove --pypi six te")).contains("pixi remove --pypi six testpath")
      assertThat(provider.getValues(context, "pixi update pyt")).contains("pixi update python")
      assertThat(provider.getValues(context, "pixi add pyth")).contains("pixi add python")
      assertThat(provider.getValues(context, "pixi add python bzi")).contains("pixi add python bzip2")
      assertThat(provider.getValues(context, "pixi add --pypi six==1.16.")).contains("pixi add --pypi six==1.16.0")
      assertThat(provider.getValues(context, "pixi add --help ")).isEmpty()
    }
  }

  @Test
  fun `dependency export preserves a Pixi manifest`(): Unit = checkDependencyExport("pixi.toml")

  @Test
  fun `dependency export preserves a Python manifest`(): Unit = checkDependencyExport("pyproject.toml")

  private fun checkDependencyExport(manifestName: String): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, environmentFactory, environmentName = "analysis", pypiDependencies = "six = '==1.16.0'") { _, manager ->
      val manifest = rootFixture.get().resolve(manifestName)
      val lock = rootFixture.get().resolve("pixi.lock")
      val beforeManifest = manifest.readBytes()
      val beforeLock = lock.readBytes()
      val exporter = manager.dependenciesExporter
      assertThat(exporter).isNotNull()
      val psiManager = PsiManager.getInstance(projectFixture.get())
      for (fileName in listOf("environment.yml", "environment.yaml")) {
        val destination = rootFixture.get().resolve(fileName)
        destination.writeText("")
        val virtualFile = withContext(Dispatchers.IO) {
          checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(destination))
        }
        val file = readAction { checkNotNull(psiManager.findFile(virtualFile)) }

        withContext(Dispatchers.EDT) { checkNotNull(exporter).export(file) }

        waitUntilAssertSucceeds("The environment export must contain the selected native and Python packages.") {
          assertThat(destination.readText()).contains("name: analysis", "python ==3.13.", "six==1.16.0", "conda-forge")
        }
      }
      val manifestFile = withContext(Dispatchers.IO) {
        checkNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(manifest))
      }
      val manifestPsi = readAction { checkNotNull(psiManager.findFile(manifestFile)) }

      withContext(Dispatchers.EDT) { checkNotNull(exporter).export(manifestPsi) }

      assertThat(manifest.readBytes()).isEqualTo(beforeManifest)
      assertThat(lock.readBytes()).isEqualTo(beforeLock)
    }
  }

  @Test
  fun `export uses the selected environment without changing workspace files`(): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(
      "pixi.toml", environmentFactory, environmentName = "analysis", pypiDependencies = "six = '==1.16.0'",
      analysisFeatures = listOf("tools"), extraManifest = "[feature.tools.pypi-dependencies]\ntestpath = '==0.6.0'\n",
    ) { _, manager ->
      val manifest = rootFixture.get().resolve("pixi.toml")
      val lock = rootFixture.get().resolve("pixi.lock")
      val beforeManifest = manifest.readBytes()
      val beforeLock = lock.readBytes()

      val output = (manager as PixiPackageManager).exportEnvironment().getOrThrow()

      assertThat(output).contains("name: analysis", "conda-forge", "python ==3.13.", "six==1.16.0", "testpath==0.6.0")
      val exported = LightVirtualFile("environment.yml", output)
      assertThat(CondaEnvironmentYmlParser.readNameFromFile(exported)).isEqualTo("analysis")
      val dependencies = CondaEnvironmentYmlParser.fromFile(exported)
      assertThat(dependencies).isNotNull()
      assertThat(dependencies.orEmpty().map { it.presentableText }).contains("six==1.16.0", "testpath==0.6.0")
      assertThat(manifest.readBytes()).isEqualTo(beforeManifest)
      assertThat(lock.readBytes()).isEqualTo(beforeLock)
    }
  }

  @Test
  fun `export reads external additions removals and upgrades from the installed prefix`(): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(
      "pixi.toml", environmentFactory, pypiDependencies = "six = '==1.16.0'\ntestpath = '==0.6.0'",
    ) { interpreter, manager ->
      val manifest = rootFixture.get().resolve("pixi.toml")
      val lock = rootFixture.get().resolve("pixi.lock")
      val beforeManifest = manifest.readBytes()
      val beforeLock = lock.readBytes()
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val script = """
        import importlib.metadata, json, pathlib, shutil, sys, sysconfig
        for name in ('six', 'testpath'):
            shutil.rmtree(importlib.metadata.distribution(name)._path)
        root = pathlib.Path(sysconfig.get_path('purelib'))
        for name, version in (('testpath', '1.0'), ('pixi_export_probe', '2.0')):
            metadata = root / (name + '-' + version + '.dist-info')
            metadata.mkdir()
            (metadata / 'METADATA').write_text('Metadata-Version: 2.1\nName: ' + name + '\nVersion: ' + version + '\n')
        native = pathlib.Path(sys.prefix) / 'conda-meta' / 'pixi_native_export_probe-3.0-0.json'
        native.write_text(json.dumps({'name': 'pixi_native_export_probe', 'version': '3.0',
                                      'channel': 'https://example.org/pixi-export-channel'}))
      """.trimIndent()
      ExecService().execGetStdout(binary, Args("-c", script)).getOrThrow()

      val result = (manager as PixiPackageManager).exportEnvironment()

      assertThat(result.isSuccess).isTrue()
      val output = result.getOrThrow()
      val dependencies = CondaEnvironmentYmlParser.fromFile(LightVirtualFile("environment.yml", output))
      assertThat(dependencies).isNotNull()
      val requirements = dependencies.orEmpty().map { it.presentableText }
      assertThat(requirements).contains("testpath==1.0", "pixi-export-probe==2.0")
      assertThat(dependencies.orEmpty().map { it.name }).doesNotContain("six")
      assertThat(requirements).doesNotContain("testpath==0.6.0")
      assertThat(output.lines()).contains(
        "  - \"https://example.org/pixi-export-channel\"",
        "  - \"https://example.org/pixi-export-channel::pixi_native_export_probe ==3.0\"",
      )
      assertThat(manifest.readBytes()).isEqualTo(beforeManifest)
      assertThat(lock.readBytes()).isEqualTo(beforeLock)
    }
  }

  @Test
  fun `export retains installed direct sources and editable directories`(): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace("pixi.toml", environmentFactory) { interpreter, manager ->
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val script = """
        import json, pathlib, sysconfig
        root = pathlib.Path(sysconfig.get_path('purelib'))
        source = root / 'pixi editable export source'
        source.mkdir()
        records = (
            ('pixi_archive_export_probe', {'url': 'https://example.org/probe-1.0.tar.gz',
                                           'archive_info': {'hashes': {'sha256': 'abcdef'}}}),
            ('pixi_vcs_export_probe', {'url': 'https://example.org/probe.git', 'subdirectory': 'python',
                                       'vcs_info': {'vcs': 'git', 'commit_id': 'abc123', 'requested_revision': 'main'}}),
            ('pixi_editable_export_probe', {'url': source.as_uri(), 'dir_info': {'editable': True}}),
        )
        for name, direct_url in records:
            metadata = root / (name + '-1.0.dist-info')
            metadata.mkdir()
            (metadata / 'METADATA').write_text('Metadata-Version: 2.1\nName: ' + name + '\nVersion: 1.0\n')
            (metadata / 'direct_url.json').write_text(json.dumps(direct_url))
        print(source.as_uri())
      """.trimIndent()
      val editableSource = ExecService().execGetStdout(binary, Args("-c", script)).getOrThrow().trim()

      val result = (manager as PixiPackageManager).exportEnvironment()

      assertThat(result.isSuccess).isTrue()
      val output = result.getOrThrow()
      assertThat(output).contains(
        "pixi-archive-export-probe @ https://example.org/probe-1.0.tar.gz#sha256=abcdef",
        "pixi-vcs-export-probe @ git+https://example.org/probe.git@abc123#subdirectory=python",
        "-e $editableSource#egg=pixi-editable-export-probe",
      )
      assertThat(output).doesNotContain("pixi-archive-export-probe==1.0", "pixi-vcs-export-probe==1.0", "pixi-editable-export-probe==1.0")
    }
  }

  @Test
  fun `export reads the installed prefix without recreating a missing lock file`(): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace("pixi.toml", environmentFactory, pypiDependencies = "six = '==1.16.0'") { _, manager ->
      val manifest = rootFixture.get().resolve("pixi.toml")
      val beforeManifest = manifest.readBytes()
      val lock = rootFixture.get().resolve("pixi.lock")
      lock.moveTo(rootFixture.get().resolve("saved-pixi.lock"))

      val result = (manager as PixiPackageManager).exportEnvironment()

      assertThat(result.isSuccess).isTrue()
      val dependencies = CondaEnvironmentYmlParser.fromFile(LightVirtualFile("environment.yml", result.getOrThrow()))
      assertThat(dependencies).isNotNull()
      assertThat(dependencies.orEmpty().map { it.presentableText }).contains("six==1.16.0")
      assertThat(lock.exists()).isFalse()
      assertThat(manifest.readBytes()).isEqualTo(beforeManifest)
    }
  }

  @Test
  fun `a source metadata probe resolves its build backend from the workspace index`(): Unit = timeoutRunBlocking(5.minutes) {
    sourceMetadataResolvesPrivateBackend(useFindLinks = false)
  }

  @Test
  fun `a source metadata probe resolves relative links from the workspace root`(): Unit = timeoutRunBlocking(5.minutes) {
    sourceMetadataResolvesPrivateBackend(useFindLinks = true)
  }

  private suspend fun sourceMetadataResolvesPrivateBackend(useFindLinks: Boolean) {
    withWorkspace("pixi.toml", environmentFactory) { interpreter, manager ->
      val root = rootFixture.get()
      val index = root.resolve("private-index").createDirectories()
      val backendName = "pixi_private_backend-1.0"
      val wheel = index.resolve("$backendName-py3-none-any.whl")
      val metadata = "$backendName.dist-info"
      val backend = """
        from pathlib import Path
        from zipfile import ZipFile

        NAME = 'pixi_private_source_probe-1.0'
        METADATA = 'Metadata-Version: 2.1\nName: pixi-private-source-probe\nVersion: 1.0\n'

        def get_requires_for_build_wheel(config_settings=None):
            return []

        def prepare_metadata_for_build_wheel(metadata_directory, config_settings=None):
            directory = Path(metadata_directory) / (NAME + '.dist-info')
            directory.mkdir()
            (directory / 'METADATA').write_text(METADATA)
            return directory.name

        def build_wheel(wheel_directory, config_settings=None, metadata_directory=None):
            filename = NAME + '-py3-none-any.whl'
            metadata = NAME + '.dist-info'
            files = {
                'pixi_private_source_probe.py': 'VALUE = 42\n',
                metadata + '/METADATA': METADATA,
                metadata + '/WHEEL': 'Wheel-Version: 1.0\nRoot-Is-Purelib: true\nTag: py3-none-any\n',
            }
            files[metadata + '/RECORD'] = ''.join(name + ',,\n' for name in files) + metadata + '/RECORD,,\n'
            with ZipFile(Path(wheel_directory) / filename, 'w') as archive:
                for name, text in files.items():
                    archive.writestr(name, text)
            return filename
      """.trimIndent()
      val files = mapOf(
        "pixi_private_backend.py" to backend,
        "$metadata/METADATA" to "Metadata-Version: 2.1\nName: pixi-private-backend\nVersion: 1.0\n",
        "$metadata/WHEEL" to "Wheel-Version: 1.0\nRoot-Is-Purelib: true\nTag: py3-none-any\n",
      )
      Compressor.Zip(wheel).use { archive ->
        for ([name, text] in files) archive.addFile(name, text.byteInputStream(), 0)
        archive.addFile("$metadata/RECORD", (files.keys + "$metadata/RECORD").joinToString("\n", postfix = "\n") {
          "$it,,"
        }.byteInputStream(), 0)
      }
      index.resolve("pixi-private-backend").createDirectories().resolve("index.html")
        .writeText("<a href='../${wheel.fileName}'>${wheel.fileName}</a>\n")
      val source = root.resolve("private source").createDirectories()
      source.resolve("pyproject.toml").writeText("""
        [build-system]
        requires = ['pixi-private-backend==1.0']
        build-backend = 'pixi_private_backend'
      """.trimIndent())
      val manifest = root.resolve("pixi.toml")
      val resolverOptions = if (useFindLinks) {
        val emptyIndex = root.resolve("empty-index").createDirectories()
        "index-url = '${emptyIndex.toUri().toASCIIString()}'\nfind-links = [{ path = 'private-index' }]\n"
      }
      else "index-url = '${index.toUri().toASCIIString()}'\n"
      manifest.writeText(manifest.readText() + "\n[pypi-options]\n$resolverOptions")
      val request = PythonPackageInstallRequest.ByLocation(source.toUri())

      val result = manager.installPackageCommand(request, emptyList())

      assertThat(result.isSuccess).isTrue()
      assertThat(manager.reloadPackages().getOrThrow().map { it.name }).contains("pixi-private-source-probe")
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val imported =
        ExecService().execGetStdout(binary, Args("-c", "import pixi_private_source_probe; print(pixi_private_source_probe.VALUE)"))
      assertThat(imported.getOrThrow().trim()).isEqualTo("42")
    }
  }

  @Test
  fun `an external Python package can be removed without changing the manifest`(): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace("pixi.toml", environmentFactory) { interpreter, manager ->
      val manifest = rootFixture.get().resolve("pixi.toml")
      val lock = rootFixture.get().resolve("pixi.lock")
      val beforeManifest = manifest.readBytes()
      val beforeLock = lock.readBytes()
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val pipProbe = "import importlib.util; print(importlib.util.find_spec('pip') is None)"
      assertThat(ExecService().execGetStdout(binary, Args("-c", pipProbe)).getOrThrow().trim()).isEqualTo("True")
      val script = """
        import pathlib, sysconfig
        root = pathlib.Path(sysconfig.get_path('purelib'))
        metadata = root / 'pixi_external_probe-1.0.dist-info'
        metadata.mkdir()
        (root / 'pixi_external_probe.py').write_text('VALUE = 42\n')
        (metadata / 'METADATA').write_text('Metadata-Version: 2.1\nName: pixi-external-probe\nVersion: 1.0\n')
        (metadata / 'RECORD').write_text('pixi_external_probe.py,,\npixi_external_probe-1.0.dist-info/METADATA,,\n'
                                        'pixi_external_probe-1.0.dist-info/RECORD,,\n')
      """.trimIndent()
      ExecService().execGetStdout(binary, Args("-c", script)).getOrThrow()
      assertThat(manager.reloadPackages().getOrThrow().map { it.name }).contains("pixi-external-probe")

      manager.uninstallPackage("pixi-external-probe").getOrThrow()

      assertThat(manager.reloadPackages().getOrThrow().map { it.name }).doesNotContain("pixi-external-probe")
      val probe = "import importlib.util; print(importlib.util.find_spec('pixi_external_probe') is None)"
      assertThat(ExecService().execGetStdout(binary, Args("-c", probe)).getOrThrow().trim()).isEqualTo("True")
      assertThat(ExecService().execGetStdout(binary, Args("-c", pipProbe)).getOrThrow().trim()).isEqualTo("True")
      assertThat(manifest.readBytes()).isEqualTo(beforeManifest)
      assertThat(lock.readBytes()).isEqualTo(beforeLock)
    }
  }

  @Test
  fun `native Python removal uses the installed pip manager`(): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace("pixi.toml", environmentFactory) { interpreter, manager ->
      val manifest = rootFixture.get().resolve("pixi.toml")
      val lock = rootFixture.get().resolve("pixi.lock")
      val beforeManifest = manifest.readBytes()
      val beforeLock = lock.readBytes()
      val trace = rootFixture.get().resolve("pip-uninstall-calls.jsonl")
      trace.writeText("")
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      ExecService().execGetStdout(binary, Args("-m", "ensurepip")).getOrThrow()
      val script = """
        import importlib.metadata, pathlib, sys, sysconfig
        root = pathlib.Path(sysconfig.get_path('purelib'))
        for name in ('pixi_remove_delegate_probe', 'pixi_keep_delegate_probe'):
            metadata = root / (name + '-1.0.dist-info')
            metadata.mkdir()
            (root / (name + '.py')).write_text('VALUE = 47\n')
            (metadata / 'METADATA').write_text('Metadata-Version: 2.1\nName: ' + name + '\nVersion: 1.0\n')
            (metadata / 'RECORD').write_text(name + '.py,,\n' + name + '-1.0.dist-info/METADATA,,\n'
                                            + name + '-1.0.dist-info/RECORD,,\n')
        pip = pathlib.Path(importlib.metadata.distribution('pip').locate_file('pip/__init__.py'))
        original = pip.read_bytes()
        pip.unlink()
        pip.write_bytes(original)
        trace = pathlib.Path(sys.argv[1])
        with pip.open('a', encoding='utf-8') as module:
            module.write('\nimport json as _pixi_json, pathlib as _pixi_pathlib, sys as _pixi_sys\n'
                         'if len(_pixi_sys.argv) > 1 and _pixi_sys.argv[1] == "uninstall":\n'
                         '    with _pixi_pathlib.Path(' + repr(str(trace)) + ').open("a", encoding="utf-8") as _pixi_trace:\n'
                         '        _pixi_trace.write(_pixi_json.dumps(_pixi_sys.argv) + "\\n")\n')
      """.trimIndent()
      ExecService().execGetStdout(binary, Args("-c", script, trace.pathString)).getOrThrow()
      assertThat(manager.reloadPackages().getOrThrow().map { it.name })
        .contains("pip", "pixi-remove-delegate-probe", "pixi-keep-delegate-probe")

      val result = manager.uninstallPackage("pixi-remove-delegate-probe")

      assertThat(result.isSuccess).isTrue()
      assertThat(result.getOrThrow().map { it.name }).contains("pip", "pixi-keep-delegate-probe")
        .doesNotContain("pixi-remove-delegate-probe")
      val calls = trace.readText().lineSequence().filter { it.isNotBlank() }.toList()
      assertThat(calls).singleElement().asString().contains("packaging_tool.py", "uninstall", "pixi-remove-delegate-probe")
      val probe = "import importlib.util, pixi_keep_delegate_probe; print(pixi_keep_delegate_probe.VALUE); " +
                  "print(importlib.util.find_spec('pixi_remove_delegate_probe') is None)"
      assertThat(ExecService().execGetStdout(binary, Args("-c", probe)).getOrThrow().trim().lineSequence().toList())
        .containsExactly("47", "True")
      assertThat(manifest.readBytes()).isEqualTo(beforeManifest)
      assertThat(lock.readBytes()).isEqualTo(beforeLock)
    }
  }

  @Test
  fun `the bundled pip helper removes the installed pip package`(): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace("pixi.toml", environmentFactory) { interpreter, manager ->
      val manifest = rootFixture.get().resolve("pixi.toml")
      val lock = rootFixture.get().resolve("pixi.lock")
      val beforeManifest = manifest.readBytes()
      val beforeLock = lock.readBytes()
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      ExecService().execGetStdout(binary, Args("-m", "ensurepip")).getOrThrow()
      assertThat(manager.reloadPackages().getOrThrow().map { it.name }).contains("pip")

      val result = manager.uninstallPackage("pip")

      assertThat(result.isSuccess).isTrue()
      assertThat(manager.reloadPackages().getOrThrow().map { it.name }).doesNotContain("pip")
      val probe = "import importlib.util; print(importlib.util.find_spec('pip') is None)"
      assertThat(ExecService().execGetStdout(binary, Args("-c", probe)).getOrThrow().trim()).isEqualTo("True")
      assertThat(manifest.readBytes()).isEqualTo(beforeManifest)
      assertThat(lock.readBytes()).isEqualTo(beforeLock)
    }
  }

  @Test
  fun `removing a feature declaration keeps a package required by the default feature`(): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(
      "pixi.toml", environmentFactory, environmentName = "analysis", pypiDependencies = "six = '==1.16.0'",
      analysisFeatures = listOf("tools"), extraManifest = "[feature.tools.pypi-dependencies]\nsix = '==1.16.0'\n",
    ) { interpreter, manager ->
      val result = manager.uninstallPackage("six", dependencyGroup = PyDependencyGroup("tools"))

      assertThat(result.isSuccess).isTrue()
      val manifest = Toml.parse(rootFixture.get().resolve("pixi.toml").readText())
      assertThat(manifest.getTable("pypi-dependencies")?.getString("six")).isEqualTo("==1.16.0")
      assertThat(manifest.getTable("feature.tools.pypi-dependencies")?.keySet().orEmpty()).doesNotContain("six")
      assertThat(manager.reloadPackages().getOrThrow().filter { it.name == "six" }.map { it.version }).containsExactly("1.16.0")
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val imported = ExecService().execGetStdout(binary, Args("-c", "import six; print(six.__version__)"))
      assertThat(imported.getOrThrow().trim()).isEqualTo("1.16.0")
    }
  }

  @Test
  fun `removing an undeclared native package preserves another external package`(): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace("pixi.toml", environmentFactory) { interpreter, manager ->
      val manifest = rootFixture.get().resolve("pixi.toml")
      val channel = createNativeChannel("pixi-remove-probe", "pixi-keep-probe")
      val executable = checkNotNull(PixiPyTool.getInstance().getCustomExecutablePath(localEel.descriptor))
      val prefix = checkNotNull(interpreter.detectedEnvironment?.pythonHomePath)
      ExecService().execGetStdout(executable, Args(
        "exec", "--spec", "micromamba", "--channel", "conda-forge", "--", "micromamba", "install", "--no-rc", "--yes",
        "--prefix", prefix.pathString, "--channel", channel.toUri().toASCIIString(),
        "--", "pixi-remove-probe", "pixi-keep-probe",
      )).getOrThrow()
      val beforeManifest = manifest.readBytes()
      val lock = rootFixture.get().resolve("pixi.lock")
      val beforeLock = lock.readBytes()
      assertThat(manager.reloadPackages().getOrThrow().map { it.name }).contains("python", "pixi-remove-probe", "pixi-keep-probe")

      manager.uninstallPackage("pixi-remove-probe").getOrThrow()

      assertThat(manager.reloadPackages().getOrThrow().map { it.name }).contains("python", "pixi-keep-probe")
        .doesNotContain("pixi-remove-probe")
      assertThat(manifest.readBytes()).isEqualTo(beforeManifest)
      assertThat(lock.readBytes()).isEqualTo(beforeLock)
    }
  }

  @Test
  fun `mixed removal preserves unrelated external native and Python packages`(): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(
      "pixi.toml", environmentFactory, condaDependencies = "six = '1.16.0'", pypiDependencies = "testpath = '==0.6.0'",
    ) { interpreter, manager ->
      val channel = createNativeChannel("pixi-remove-probe", "pixi-keep-probe")
      val executable = checkNotNull(PixiPyTool.getInstance().getCustomExecutablePath(localEel.descriptor))
      val prefix = checkNotNull(interpreter.detectedEnvironment?.pythonHomePath)
      ExecService().execGetStdout(executable, Args(
        "exec", "--spec", "micromamba", "--channel", "conda-forge", "--", "micromamba", "install", "--no-rc", "--yes",
        "--prefix", prefix.pathString, "--channel", channel.toUri().toASCIIString(),
        "--", "pixi-remove-probe", "pixi-keep-probe",
      )).getOrThrow()
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val script = """
        import pathlib, sysconfig
        root = pathlib.Path(sysconfig.get_path('purelib'))
        for name in ('pixi_remove_python_probe', 'pixi_keep_python_probe'):
            metadata = root / (name + '-1.0.dist-info')
            metadata.mkdir()
            (root / (name + '.py')).write_text('VALUE = 44\n')
            (metadata / 'METADATA').write_text('Metadata-Version: 2.1\nName: ' + name + '\nVersion: 1.0\n')
            (metadata / 'RECORD').write_text(name + '.py,,\n' + name + '-1.0.dist-info/METADATA,,\n'
                                            + name + '-1.0.dist-info/RECORD,,\n')
      """.trimIndent()
      ExecService().execGetStdout(binary, Args("-c", script)).getOrThrow()
      assertThat(manager.reloadPackages().getOrThrow().map { it.name }).contains(
        "six", "testpath", "pixi-remove-probe", "pixi-keep-probe", "pixi-remove-python-probe", "pixi-keep-python-probe",
      )

      val result = manager.uninstallPackage("six", "testpath", "pixi-remove-probe", "pixi-remove-python-probe")

      assertThat(result.isSuccess).isTrue()
      assertThat(result.getOrThrow().map { it.name }).contains("python", "pixi-keep-probe", "pixi-keep-python-probe")
        .doesNotContain("six", "testpath", "pixi-remove-probe", "pixi-remove-python-probe")
      val manifest = Toml.parse(rootFixture.get().resolve("pixi.toml").readText())
      assertThat(manifest.getTable("dependencies")?.keySet()).contains("python").doesNotContain("six")
      assertThat(manifest.getTable("pypi-dependencies")?.keySet().orEmpty()).doesNotContain("testpath")
      assertThat(ExecService().execGetStdout(binary, Args(
        "-c",
        "import importlib.util, pixi_keep_python_probe; print(pixi_keep_python_probe.VALUE); " +
        "print(importlib.util.find_spec('pixi_remove_python_probe') is None)",
      )).getOrThrow().trim().lineSequence().toList()).containsExactly("44", "True")
    }
  }

  @Test
  fun `installed inventory survives a missing lock file without recreating it`(): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace("pixi.toml", environmentFactory, pypiDependencies = "six = '==1.16.0'") { _, manager ->
      val manifest = rootFixture.get().resolve("pixi.toml")
      val beforeManifest = manifest.readBytes()
      val lock = rootFixture.get().resolve("pixi.lock")
      lock.moveTo(rootFixture.get().resolve("saved-pixi.lock"))

      val result = manager.reloadPackages()

      assertThat(result.isSuccess).isTrue()
      assertThat(result.getOrThrow().map { it.name }).contains("python", "six")
      assertThat(result.getOrThrow().single { it.name == "six" }.version).isEqualTo("1.16.0")
      assertThat(lock.exists()).isFalse()
      assertThat(manifest.readBytes()).isEqualTo(beforeManifest)
    }
  }

  @Test
  fun `package inventory reads external changes without restoring the lock state`(): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace("pixi.toml", environmentFactory, pypiDependencies = "six = '==1.16.0'") { interpreter, manager ->
      val manifest = rootFixture.get().resolve("pixi.toml")
      val lock = rootFixture.get().resolve("pixi.lock")
      val beforeManifest = manifest.readBytes()
      val beforeLock = lock.readBytes()
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val script = """
        import importlib.metadata, json, pathlib, shutil, sys, sysconfig
        shutil.rmtree(importlib.metadata.distribution('six')._path)
        path = pathlib.Path(sysconfig.get_path('purelib')) / 'pixi_inventory_probe-1.0.dist-info'
        path.mkdir()
        (path / 'METADATA').write_text('Metadata-Version: 2.1\nName: pixi-inventory-probe\nVersion: 1.0\n')
        native = pathlib.Path(sys.prefix) / 'conda-meta' / 'pixi_native_inventory_probe-2.0-0.json'
        native.write_text(json.dumps({'name': 'pixi_native_inventory_probe', 'version': '2.0'}))
      """.trimIndent()
      ExecService().execGetStdout(binary, Args("-c", script)).getOrThrow()

      val nativeRecords = readPixiCondaPackagesFromInterpreter(interpreter).getOrThrow()
      assertThat(nativeRecords.map { it.name }).contains("python", "pixi_native_inventory_probe")
      val result = manager.reloadPackages()

      assertThat(result.isSuccess).isTrue()
      assertThat(result.getOrThrow().map { it.name }).contains("pixi-inventory-probe").doesNotContain("six")
      val pkg = result.getOrThrow().single { it.name == "pixi-inventory-probe" }
      assertThat(pkg.version).isEqualTo("1.0")
      assertThat(pkg.defaultRepository).isSameAs(PyPiPackageRepository)
      val native = result.getOrThrow().single { it.name == "pixi-native-inventory-probe" }
      assertThat(native.version).isEqualTo("2.0")
      assertThat(native.defaultRepository).isInstanceOf(PixiNativeRepository::class.java)
      assertThat(manifest.readBytes()).isEqualTo(beforeManifest)
      assertThat(lock.readBytes()).isEqualTo(beforeLock)
    }
  }

  @Test
  fun `a setup script installs without static project metadata or pip`(): Unit = timeoutRunBlocking(5.minutes) {
    val source = rootFixture.get().resolve("legacy source with spaces").createDirectories()
    source.resolve("setup.py").writeText(
      "from setuptools import setup\nsetup(name='pixi-legacy-source-probe', version='1.0', py_modules=['legacy_probe'])\n",
    )
    source.resolve("legacy_probe.py").writeText("VALUE = 42\n")
    withWorkspace("pixi.toml", environmentFactory) { interpreter, manager ->
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val hasNoPip = "import importlib.util; print(importlib.util.find_spec('pip') is None)"
      assertThat(ExecService().execGetStdout(binary, Args("-c", hasNoPip)).getOrThrow().trim()).isEqualTo("True")

      val result = manager.installPackageCommand(PythonPackageInstallRequest.ByLocation(source.toUri()), emptyList())

      assertThat(result.isSuccess).describedAs(result.toString()).isTrue()
      assertThat(manager.reloadPackages().getOrThrow().map { it.name }).contains("pixi-legacy-source-probe")
      assertThat(ExecService().execGetStdout(binary, Args("-c", "import legacy_probe; print(legacy_probe.VALUE)"))
                   .getOrThrow().trim()).isEqualTo("42")
      assertThat(ExecService().execGetStdout(binary, Args("-c", hasNoPip)).getOrThrow().trim()).isEqualTo("True")
      assertThat(source.resolve("pyproject.toml")).doesNotExist()
      val manifest = rootFixture.get().resolve("pixi.toml").readText()
      assertThat(manifest).contains("# Keep the workspace comment.")
      assertThat(Toml.parse(manifest).getTable("pypi-dependencies")?.keySet()).containsExactly("pixi-legacy-source-probe")
    }
  }

  @Test
  fun `a build backend supplies the source name without a project table`(): Unit = timeoutRunBlocking(5.minutes) {
    val source = rootFixture.get().resolve("backend source").createDirectories()
    val sourceManifest = "[build-system]\nrequires = ['setuptools']\nbuild-backend = 'setuptools.build_meta'\n"
    source.resolve("pyproject.toml").writeText(sourceManifest)
    source.resolve("setup.py").writeText(
      "from setuptools import setup\nsetup(name='pixi-backend-source-probe', version='1.0', py_modules=['backend_probe'])\n",
    )
    source.resolve("backend_probe.py").writeText("VALUE = 43\n")
    withWorkspace("pixi.toml", environmentFactory) { interpreter, manager ->
      val result = manager.installPackageCommand(PythonPackageInstallRequest.ByLocation(source.toUri()), emptyList())

      assertThat(result.isSuccess).describedAs(result.toString()).isTrue()
      assertThat(manager.reloadPackages().getOrThrow().map { it.name }).contains("pixi-backend-source-probe")
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      assertThat(ExecService().execGetStdout(binary, Args("-c", "import backend_probe; print(backend_probe.VALUE)"))
                   .getOrThrow().trim()).isEqualTo("43")
      assertThat(source.resolve("pyproject.toml").readText()).isEqualTo(sourceManifest)
      assertThat(Toml.parse(rootFixture.get().resolve("pixi.toml").readText()).getTable("pypi-dependencies")?.keySet())
        .containsExactly("pixi-backend-source-probe")
    }
  }

  @Test
  fun `a failed source metadata build preserves the workspace manifest`(): Unit = timeoutRunBlocking(5.minutes) {
    val source = rootFixture.get().resolve("broken source").createDirectories()
    source.resolve("setup.py").writeText("raise RuntimeError('The source metadata build failed.')\n")
    withWorkspace("pixi.toml", environmentFactory) { _, manager ->
      val manifest = rootFixture.get().resolve("pixi.toml")
      val lock = rootFixture.get().resolve("pixi.lock")
      val beforeManifest = manifest.readBytes()
      val beforeLock = lock.readBytes()

      val result = manager.installPackageCommand(PythonPackageInstallRequest.ByLocation(source.toUri()), emptyList())

      assertThat(result.isSuccess).isFalse()
      assertThat(manifest.readBytes()).isEqualTo(beforeManifest)
      assertThat(lock.readBytes()).isEqualTo(beforeLock)
    }
  }

  @Test
  fun `a source archive uses its metadata instead of its filename`(): Unit = timeoutRunBlocking(5.minutes) {
    val archive = rootFixture.get().resolve("wrong_name-1.0.tar.gz")
    Compressor.Tar(archive, Compressor.Tar.Compression.GZIP).use { tar ->
      val files = mapOf(
        "pyproject.toml" to """
          [project]
          name = 'pixi-archive-probe'
          version = '1.0'
          [build-system]
          requires = []
          build-backend = 'backend'
          backend-path = ['.']
        """.trimIndent(),
        "backend.py" to """
          from pathlib import Path
          from zipfile import ZipFile
          def build_wheel(wheel_directory, config_settings=None, metadata_directory=None):
              name = 'pixi_archive_probe-1.0-py3-none-any.whl'
              metadata = 'pixi_archive_probe-1.0.dist-info'
              files = {
                  'pixi_archive_probe.py': 'VALUE = 42\n',
                  metadata + '/METADATA': 'Metadata-Version: 2.1\nName: pixi-archive-probe\nVersion: 1.0\n',
                  metadata + '/WHEEL': 'Wheel-Version: 1.0\nRoot-Is-Purelib: true\nTag: py3-none-any\n',
              }
              files[metadata + '/RECORD'] = ''.join(path + ',,\n' for path in list(files) + [metadata + '/RECORD'])
              with ZipFile(Path(wheel_directory) / name, 'w') as wheel:
                  for path, content in files.items():
                      wheel.writestr(path, content)
              return name
        """.trimIndent(),
        "PKG-INFO" to "Metadata-Version: 2.1\nName: pixi-archive-probe\nVersion: 1.0\n",
      )
      for ([name, content] in files) tar.addFile("pixi_archive_probe-1.0/$name", content.byteInputStream(), 0)
    }
    withWorkspace("pixi.toml", environmentFactory) { interpreter, manager ->
      val result = manager.installPackageCommand(PythonPackageInstallRequest.ByLocation(archive.toUri()), emptyList())
      assertThat(result.isSuccess).describedAs(result.toString()).isTrue()
      assertThat(manager.reloadPackages().getOrThrow().map { it.name }).contains("pixi-archive-probe")
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      assertThat(ExecService().execGetStdout(binary, Args("-c", "import pixi_archive_probe; print(pixi_archive_probe.VALUE)"))
                   .getOrThrow().trim()).isEqualTo("42")
      val dependencies = Toml.parse(rootFixture.get().resolve("pixi.toml").readText()).getTable("pypi-dependencies")
      assertThat(dependencies?.keySet()).containsExactly("pixi-archive-probe")
      assertThat(dependencies?.getTable(listOf("pixi-archive-probe"))?.getString("path")).isEqualTo(archive.pathString)
    }
  }

  @Test
  fun `a local wheel installs through its file requirement`(): Unit = timeoutRunBlocking(5.minutes) {
    val wheel = rootFixture.get().resolve("pixi_wheel_probe-1.0-py3-none-any.whl")
    val metadata = "pixi_wheel_probe-1.0.dist-info"
    val files = mapOf(
      "pixi_wheel_probe/__init__.py" to "VALUE = 42\n",
      "$metadata/METADATA" to "Metadata-Version: 2.1\nName: pixi-wheel-probe\nVersion: 1.0\n",
      "$metadata/WHEEL" to "Wheel-Version: 1.0\nGenerator: test\nRoot-Is-Purelib: true\nTag: py3-none-any\n",
    )
    Compressor.Zip(wheel).use { archive ->
      for ([name, text] in files) archive.addFile(name, text.byteInputStream(), 0)
      val record = (files.keys + "$metadata/RECORD").joinToString("\n", postfix = "\n") { "$it,," }
      archive.addFile("$metadata/RECORD", record.byteInputStream(), 0)
    }
    withWorkspace("pyproject.toml", environmentFactory) { interpreter, manager ->
      val request = PythonPackageInstallRequest.ByLocation(wheel.toUri())
      val manifest = rootFixture.get().resolve("pyproject.toml")
      val lock = rootFixture.get().resolve("pixi.lock")
      val manifestBefore = manifest.readBytes()
      val lockBefore = lock.readBytes()
      assertThat(manager.installPackageCommand(request, listOf("-e")).isSuccess).isFalse()
      assertThat(manifest.readBytes()).isEqualTo(manifestBefore)
      assertThat(lock.readBytes()).isEqualTo(lockBefore)
      assertThat(manager.installPackageCommand(request, emptyList()).isSuccess).isTrue()
      val installed = manager.reloadPackages().getOrThrow()
      assertThat(installed.single { it.name == "pixi-wheel-probe" }.version).isEqualTo("1.0")
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val imported = ExecService().execGetStdout(binary, Args("-c", "import pixi_wheel_probe; print(pixi_wheel_probe.VALUE)"))
      assertThat(imported.getOrThrow().trim()).isEqualTo("42")
    }
  }

  @Test
  fun `location updates cannot duplicate existing dependency declarations`(): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(
      "pyproject.toml", environmentFactory, environmentName = "analysis", analysisFeatures = listOf("tools"),
      pypiDependencies = "six = '==1.16.0'",
      extraManifest = "[dependency-groups]\ntools = ['testpath==0.5.0']\n",
    ) { _, manager ->
      val manifest = rootFixture.get().resolve("pyproject.toml")
      val lock = rootFixture.get().resolve("pixi.lock")
      val manifestBefore = manifest.readBytes()
      val lockBefore = lock.readBytes()
      val wheel = PythonPackageInstallRequest.ByLocation(URI("https://example.org/six-1.17.0-py2.py3-none-any.whl"))
      assertThat(manager.installPackageCommand(wheel, emptyList()).isSuccess).isFalse()
      assertThat(manifest.readBytes()).isEqualTo(manifestBefore)
      assertThat(lock.readBytes()).isEqualTo(lockBefore)
      val localPackage = rootFixture.get().resolve("local-testpath").createDirectories()
      localPackage.resolve("pyproject.toml").writeText("[project]\nname = 'testpath'\nversion = '0.5.0'\n")
      val path = PythonPackageInstallRequest.ByLocation(localPackage.toUri())
      assertThat(manager.installPackageCommand(path, emptyList()).isSuccess).isFalse()
      assertThat(manifest.readBytes()).isEqualTo(manifestBefore)
      assertThat(lock.readBytes()).isEqualTo(lockBefore)
    }
  }

  @Test
  fun `requirement quick fixes preserve source declarations`(): Unit = timeoutRunBlocking(5.minutes) {
    val channel = createNativeChannel("pixi_native_probe", "pixi-native-probe")
    withWorkspace(
      "pyproject.toml", environmentFactory, environmentName = "analysis", analysisFeatures = listOf("tools"),
      pypiDependencies = "six = '==1.16.0'",
      extraManifest = """
        [tool.pixi.feature.tools]
        channels = ['${channel.toUri()}']
        [tool.pixi.feature.tools.dependencies]
        pixi_native_probe = '1.2.3'
      """.trimIndent(),
    ) { interpreter, manager ->
      val requirement = checkNotNull(PyRequirementParser.fromLine("six==1.17.0", projectFixture.get()))
      assertThat(manager.addDependencyToFile(requirement)).isTrue()
      val manifest = Toml.parse(rootFixture.get().resolve("pyproject.toml").readText())
      assertThat(manifest.getTable("tool.pixi.pypi-dependencies")?.keySet()).containsExactly("six")
      assertThat(manifest.getArray("project.dependencies")).isNull()
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val imported = ExecService().execGetStdout(binary, Args("-c", "import six; print(six.__version__)"))
      assertThat(imported.getOrThrow().trim()).isEqualTo("1.17.0")
      val nativeRequirement = checkNotNull(PyRequirementParser.fromLine("pixi-native-probe==1.2.3", projectFixture.get()))
      assertThat(manager.addDependencyToFile(nativeRequirement)).isTrue()
      val updated = Toml.parse(rootFixture.get().resolve("pyproject.toml").readText())
      assertThat(updated.getTable("tool.pixi.feature.tools.dependencies")?.keySet()).containsExactly("pixi_native_probe")
    }
  }

  @Test
  fun `a custom index cannot duplicate a Python dependency array`(): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(
      "pyproject.toml", environmentFactory, environmentName = "analysis", analysisFeatures = listOf("tools"),
      extraManifest = """
        [dependency-groups]
        tools = ['six==1.16.0', {include-group = 'shared'}]
        shared = ['testpath==0.5.0']
      """.trimIndent(),
    ) { _, manager ->
      val manifest = rootFixture.get().resolve("pyproject.toml")
      val lock = rootFixture.get().resolve("pixi.lock")
      val manifestBefore = manifest.readBytes()
      val lockBefore = lock.readBytes()
      val repository = PyPackageRepository("Explicit index", "https://pypi.org/simple/", null)
      val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(listOf(
        PythonRepositoryPackageSpecification(PyPiPackageRepository, "six", "1.17.0"),
        PythonRepositoryPackageSpecification(repository, "testpath", "0.5.0"),
      ))
      val result = manager.installPackageCommand(request, emptyList())
      assertThat(result.isSuccess).isFalse()
      assertThat(manifest.readBytes()).isEqualTo(manifestBefore)
      assertThat(lock.readBytes()).isEqualTo(lockBefore)
      val optionRequest = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(listOf(
        PythonRepositoryPackageSpecification(PyPiPackageRepository, "testpath", "0.5.0"),
      ))
      val optionResult = manager.installPackageCommand(optionRequest, listOf("--index=https://pypi.org/simple/"))
      assertThat(optionResult.isSuccess).isFalse()
      assertThat(manifest.readBytes()).isEqualTo(manifestBefore)
      assertThat(lock.readBytes()).isEqualTo(lockBefore)
    }
  }

  @Test
  fun `custom options cannot move a Python dependency into a second declaration`(): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(
      "pyproject.toml", environmentFactory, environmentName = "analysis", analysisFeatures = listOf("tools"),
      extraManifest = "[dependency-groups]\ntools = ['six==1.16.0']\n",
    ) { _, manager ->
      val manifest = rootFixture.get().resolve("pyproject.toml")
      val lock = rootFixture.get().resolve("pixi.lock")
      val beforeManifest = manifest.readBytes()
      val beforeLock = lock.readBytes()
      val repositoryRequest = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(listOf(
        PythonRepositoryPackageSpecification(PyPiPackageRepository, "six", "1.17.0"),
      ))
      val locationRequest = PythonPackageInstallRequest.ByLocation(URI("https://example.org/six-1.17.0-py3-none-any.whl"))
      val cases = listOf(
        repositoryRequest to listOf("--platform=osx-arm64"),
        repositoryRequest to listOf("--editable"),
        locationRequest to listOf("--index=https://pypi.org/simple/"),
        locationRequest to listOf("-p", "osx-arm64"),
      )
      for ([request, options] in cases) {
        val result = manager.installPackageCommand(request, options)
        assertThat(result.isSuccess).describedAs(options.toString()).isFalse()
        assertThat(manifest.readBytes()).describedAs(options.toString()).isEqualTo(beforeManifest)
        assertThat(lock.readBytes()).describedAs(options.toString()).isEqualTo(beforeLock)
      }
    }
  }

  @Test
  fun `mixed dependency groups retain the owning group during updates`(): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(
      "pyproject.toml", environmentFactory, environmentName = "analysis", analysisFeatures = listOf("tools"),
      extraManifest = """
        [dependency-groups]
        tools = ['six==1.16.0', {include-group = 'shared'}]
        shared = ['testpath==0.5.0']
      """.trimIndent(),
    ) { interpreter, manager ->
      assertThat(interpreter.ref.envRef.value).isEqualTo("analysis")
      assertThat(rootFixture.get().resolve("pyproject.toml").readText()).contains("tools = ['six==1.16.0'")
      val result = manager.updatePackages(
        PythonRepositoryPackageSpecification(PyPiPackageRepository, "six", "1.17.0"),
        PythonRepositoryPackageSpecification(PyPiPackageRepository, "testpath", "0.6.0"),
      )
      assertThat(result.isSuccess).describedAs(rootFixture.get().resolve("pyproject.toml").readText()).isTrue()
      val packages = result.getOrThrow().associate { it.name to it.version }
      assertThat(packages).containsEntry("six", "1.17.0").containsEntry("testpath", "0.6.0")
      val manifest = rootFixture.get().resolve("pyproject.toml").readText()
      assertThat(manifest).contains("include-group = 'shared'").doesNotContain("pypi-dependencies")
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val imported = ExecService().execGetStdout(binary, Args("-c", "import six, testpath; print(six.__version__, testpath.__version__)"))
      assertThat(imported.getOrThrow().trim()).isEqualTo("1.17.0 0.6.0")
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `concurrent package installs preserve both dependencies`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, factory) { interpreter, manager ->
      coroutineScope {
        listOf("six" to "1.17.0", "testpath" to "0.6.0").map { [name, version] ->
          async {
            val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(
              listOf(PythonRepositoryPackageSpecification(PyPiPackageRepository, name, version)),
            )
            manager.installPackageCommand(request, emptyList()).getOrThrow()
          }
        }.awaitAll()
      }
      assertThat(declaredPackages(manifestName)).containsExactlyInAnyOrder("six", "testpath")
      assertThat(manager.reloadPackages().getOrThrow().map { it.name }).contains("six", "testpath")
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val imported = ExecService().execGetStdout(binary, Args("-c", "import six, testpath; print(six.__version__, testpath.__version__)"))
      assertThat(imported.getOrThrow().trim()).isEqualTo("1.17.0 0.6.0")
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `the browser default group adds to the root instead of changing an inherited feature`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    withWorkspace(
      manifestName, factory, environmentName = "analysis", analysisFeatures = listOf("tools"),
      extraManifest = "[${prefix}feature.tools.pypi-dependencies]\nsix = '==1.16.0'\n",
    ) { interpreter, manager ->
      val groups = manager.workspaceSupport?.getDependencyGroups(ProjectName("packaging-test"))?.values?.flatten().orEmpty()
      val defaultGroup = groups.single { it.name == "default" }
      assertThat(groups.map { it.name }).doesNotContain("main")
      val support = checkNotNull(PyProjectManager.forPythonInterpreter(interpreter).dependencyGroupSupport)
      val options = support.formatDependencyGroupArgs(interpreter, defaultGroup, null)
      assertThat(options).containsExactly("--feature=default")
      val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(
        listOf(PythonRepositoryPackageSpecification(PyPiPackageRepository, "six", "1.16.0")),
      )
      assertThat(manager.installPackageCommand(request, options).isSuccess).isTrue()
      assertThat(declaredPackages(manifestName)).containsExactly("six")
      val manifest = Toml.parse(rootFixture.get().resolve(manifestName).readText())
      assertThat(manifest.getTable("${prefix}feature.tools.pypi-dependencies")?.getString("six")).isEqualTo("==1.16.0")
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `an editable VCS request cannot silently become a regular install`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, factory) { _, manager ->
      val manifest = rootFixture.get().resolve(manifestName)
      val before = manifest.readText()
      val request = PythonPackageInstallRequest.ByLocation(URI("git+https://example.org/app.git#egg=app"))
      assertThat(manager.installPackageCommand(request, listOf("-e")).isSuccess).isFalse()
      assertThat(manager.installPackageCommand(request, listOf("--editable")).isSuccess).isFalse()
      assertThat(manifest.readText()).isEqualTo(before)
    }
  }

  @ParameterizedTest
  @CsvSource("pixi.toml, false", "pyproject.toml, false", "pixi.toml, true", "pyproject.toml, true")
  fun `a removal batch removes packages from different dependency sources and scopes`(
    manifestName: String, mixedSources: Boolean, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    withWorkspace(
      manifestName, factory, environmentName = "analysis", pypiDependencies = "six = '==1.16.0'",
      condaDependencies = if (mixedSources) "testpath = '0.6.0'" else "",
      analysisFeatures = if (mixedSources) emptyList() else listOf("tools"),
      extraManifest = if (mixedSources) "" else "[${prefix}feature.tools.pypi-dependencies]\ntestpath = '==0.6.0'\n",
    ) { _, manager ->
      val result = manager.uninstallPackage("six", "testpath")
      result.getOrThrow()
      val installed = manager.reloadPackages().getOrThrow()
      assertThat(installed.map { it.name }).doesNotContain("six", "testpath")
      val manifest = Toml.parse(rootFixture.get().resolve(manifestName).readText())
      assertThat(manifest.getTable("${prefix}pypi-dependencies")?.keySet().orEmpty()).doesNotContain("six")
      if (!mixedSources) {
        assertThat(manifest.getTable("${prefix}feature.tools")).isNotNull()
        assertThat(manifest.getTable("${prefix}feature.tools.pypi-dependencies")?.keySet().orEmpty()).doesNotContain("testpath")
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `one batch installs and updates native and PyPI packages`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, factory) { _, manager ->
      val specifications = listOf(
        PythonRepositoryPackageSpecification(CondaPackageRepository, "testpath", "0.6.0"),
        PythonRepositoryPackageSpecification(PyPiPackageRepository, "six", "1.16.0"),
      )
      val result = manager.installPackageCommand(
        PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(specifications), emptyList(),
      )
      assertThat(result.isSuccess).isTrue()
      assertThat(manager.reloadPackages().getOrThrow().filter { it.name in setOf("six", "testpath") }
                   .associate { it.name to it.version }).containsExactlyInAnyOrderEntriesOf(mapOf("six" to "1.16.0", "testpath" to "0.6.0"))

      val update = manager.updatePackages(
        specifications.first(), PythonRepositoryPackageSpecification(PyPiPackageRepository, "six", "1.17.0"),
      )
      assertThat(update.isSuccess).isTrue()
      assertThat(manager.reloadPackages().getOrThrow().filter { it.name in setOf("six", "testpath") }
                   .associate { it.name to it.version }).containsExactlyInAnyOrderEntriesOf(mapOf("six" to "1.17.0", "testpath" to "0.6.0"))
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `a later source failure leaves completed groups installed`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, factory) { interpreter, manager ->
      val result = manager.installPackageCommand(
        PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(listOf(
          PythonRepositoryPackageSpecification(CondaPackageRepository, "testpath", "0.6.0"),
          PythonRepositoryPackageSpecification(PyPiPackageRepository, "six", "999999.0"),
        )),
        emptyList(),
      )
      assertThat(result.isSuccess).isFalse()
      assertThat(manager.reloadPackages().getOrThrow().map { it.name }).contains("testpath").doesNotContain("six")
      val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
      val manifest = Toml.parse(rootFixture.get().resolve(manifestName).readText())
      assertThat(manifest.getTable("${prefix}dependencies")?.keySet()).contains("testpath")
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val imported = ExecService().execGetStdout(binary, Args("-c", "import testpath; print(testpath.__version__)"))
      assertThat(imported.getOrThrow().trim()).isEqualTo("0.6.0")
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `one removal batch removes every package in the selected feature`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    withWorkspace(
      manifestName, factory, environmentName = "analysis", analysisFeatures = listOf("tools"),
      extraManifest = "[${prefix}feature.tools.pypi-dependencies]\nsix = '==1.16.0'\ntestpath = '==0.6.0'\n",
    ) { _, manager ->
      val result = manager.uninstallPackage("six", "testpath")
      assertThat(result.isSuccess).isTrue()
      assertThat(result.getOrThrow().map { it.name }).doesNotContain("six", "testpath")
      val manifest = Toml.parse(rootFixture.get().resolve(manifestName).readText())
      assertThat(manifest.getTable("${prefix}feature.tools")).isNotNull()
      assertThat(manifest.getArray("${prefix}environments.analysis")?.toList()).containsExactly("tools")
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `native search uses only the selected environment channels`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val channel = createNativeChannel("pixi_native_probe")
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    withWorkspace(
      manifestName, factory, environmentName = "analysis", analysisFeatures = listOf("tools"),
      extraManifest = "[${prefix}feature.tools]\nchannels = ['${channel.toUri()}']\n",
    ) { _, manager ->
      val repository = manager.repositoryManager
      val nativeRepository = repository.repositories.filterIsInstance<PixiNativeRepository>().single()
      val search = nativeRepository.search("pixi_native_probe")
      assertThat(search.pages.flatMap { it.contents().getOrThrow() }).containsExactly("pixi_native_probe")
      val details = nativeRepository.buildPackageDetails("pixi_native_probe")
      assertThat(details.isSuccess).isTrue()
      assertThat(details.getOrThrow().availableVersions).containsExactly("1.2.3")
      val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(
        listOf(PythonRepositoryPackageSpecification(nativeRepository, "pixi-native-probe", "1.2.3")),
      )
      assertThat(manager.installPackageCommand(request, emptyList()).isSuccess).isTrue()
      val installed = manager.reloadPackages().getOrThrow()
      assertThat(installed.filter { it.name == "pixi-native-probe" }).singleElement()
        .extracting { it.defaultRepository }.isSameAs(nativeRepository)
      val popupDetails = repository.getPackageDetails("pixi-native-probe", null).getOrThrow()
      assertThat(popupDetails.name).isEqualTo("pixi_native_probe")
      assertThat(popupDetails.availableVersions).containsExactly("1.2.3")
      val defaultInterpreter = checkNotNull(pyProjectFixture.get().getInterpreter())
      val defaultManager = PythonPackageManager.forPythonInterpreter(projectFixture.get(), defaultInterpreter)
      val defaultSearch = defaultManager.repositoryManager.searchPackages(CondaPackageRepository, "pixi_native_probe")
      assertThat(defaultSearch.pages.flatMap { it.contents().getOrThrow() }).isEmpty()
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `browser feature options change only the selected feature`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    withWorkspace(
      manifestName, factory, environmentName = "analysis", pypiDependencies = "six = '==1.16.0'",
      extraManifest = "[${prefix}feature.tools]\n", analysisFeatures = listOf("tools"),
    ) { interpreter, manager ->
      val support = PyProjectManager.forPythonInterpreter(interpreter).dependencyGroupSupport
      assertThat(support).isNotNull()
      val options = support?.formatDependencyGroupArgs(interpreter, PyDependencyGroup("tools"), null).orEmpty()
      assertThat(options).containsExactly("--feature=tools")
      assertThat(manager.workspaceSupport).isNotNull()
      val groups = manager.workspaceSupport?.getDependencyGroups(ProjectName("packaging-test"))
      assertThat(groups?.values?.flatten()?.map { it.name }).containsExactly("default", "tools")
      val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(
        listOf(PythonRepositoryPackageSpecification(PyPiPackageRepository, "testpath", "0.6.0")),
      )
      val result = manager.installPackageCommand(request, options)
      assertThat(result.isSuccess).isTrue()
      val parsed = Toml.parse(rootFixture.get().resolve(manifestName).readText())
      val featureNames = parsed.getTable("${prefix}feature.tools.pypi-dependencies")?.keySet().orEmpty() +
                         parsed.getArray("dependency-groups.tools")?.toList().orEmpty().mapNotNull {
                           PyRequirementParser.fromLine(it as String, projectFixture.get())?.name
                         }
      assertThat(featureNames).containsExactly("testpath")
      assertThat(declaredPackages(manifestName)).containsExactly("six")
      val packages = manager.reloadPackages()
      assertThat(packages.isSuccess).isTrue()
      assertThat(packages.getOrThrow().filter { it.name == "testpath" }).singleElement()
        .extracting { it.version }.isEqualTo("0.6.0")
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `an editable location install updates the manifest and interpreter`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, factory, workspacePackage = true) { interpreter, manager ->
      val location = rootFixture.get().resolve("packages/app").toUri()
      val result = manager.installPackageCommand(PythonPackageInstallRequest.ByLocation(location), listOf("-e"))
      assertThat(result.isSuccess).isTrue()
      val packages = manager.reloadPackages()
      assertThat(packages.isSuccess).isTrue()
      assertThat(packages.getOrThrow().filter { it.name == "app" }).singleElement().extracting { it.isEditableMode }.isEqualTo(true)
      assertThat(declaredPackages(manifestName)).contains("app")
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val imported = ExecService().execGetStdout(binary, Args("-c", "import app; print(app.VALUE)"))
      assertThat(imported.isSuccess).isTrue()
      assertThat(imported.getOrThrow().trim()).isEqualTo("42")
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `a retained interpreter cannot recreate a deleted environment`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, factory, environmentName = "analysis") { _, manager ->
      val manifest = rootFixture.get().resolve(manifestName)
      manifest.writeText(manifest.readText().replace("analysis = []", ""))
      val before = manifest.readText()
      val lock = rootFixture.get().resolve("pixi.lock")
      val lockBefore = lock.readText()
      val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(
        listOf(PythonRepositoryPackageSpecification(PyPiPackageRepository, "six", "1.17.0")),
      )
      val result = manager.installPackageCommand(request, emptyList())
      assertThat(result.isSuccess).isFalse()
      assertThat(manifest.readText()).isEqualTo(before)
      assertThat(lock.readText()).isEqualTo(lockBefore)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `the native browser can find and install a Conda package`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, factory) { interpreter, manager ->
      val repository = manager.repositoryManager
      val nativeRepository = repository.repositories.filterIsInstance<PixiNativeRepository>().single()
      val search = nativeRepository.search("six")
      assertThat(search.pages.flatMap { it.contents().getOrThrow() }).contains("six")
      val requirement = checkNotNull(PyRequirementParser.fromLine("six==1.17.0", projectFixture.get()))
      val specification = nativeRepository.findPackageSpecification(requirement)
      assertThat(specification).isNotNull()
      val installed = manager.updatePackages(checkNotNull(specification))
      assertThat(installed.isSuccess).isTrue()
      assertThat(installed.getOrThrow().filter { it.name == "six" }).singleElement()
        .satisfies({ assertThat(it.version).isEqualTo("1.17.0") }, { assertThat(it.defaultRepository).isSameAs(nativeRepository) })
      val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
      val manifest = Toml.parse(rootFixture.get().resolve(manifestName).readText())
      assertThat(manifest.getTable("${prefix}dependencies")?.keySet()).contains("six")
      assertThat(declaredPackages(manifestName)).doesNotContain("six")
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val imported = ExecService().execGetStdout(binary, Args("-c", "import six; print(six.__version__)"))
      assertThat(imported.isSuccess).isTrue()
      assertThat(imported.getOrThrow().trim()).isEqualTo("1.17.0")
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `native outdated detection leaves the workspace unchanged`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    val foreignPlatform = if (SystemInfo.isWindows) "linux-64" else "win-64"
    withWorkspace(
      manifestName, factory, condaDependencies = "six = '1.16.0'",
      beforeManager = { manifest ->
        manifest.writeText(manifest.readText().replace("six = '1.16.0'", "six = '>=1.16.0'") +
                           "\n[${prefix}target.$foreignPlatform.dependencies]\nsix = '1.16.0'\n")
      },
    ) { _, manager ->
      val manifest = rootFixture.get().resolve(manifestName)
      val lock = rootFixture.get().resolve("pixi.lock")
      val manifestBefore = manifest.readText()
      val lockBefore = lock.readText()
      waitUntilAssertSucceeds("The native update must reach the package manager cache.") {
        assertThat(manager.listOutdatedPackages()["six"]).isNotNull()
      }
      val outdated = manager.listOutdatedPackages()
      assertThat(outdated["six"]?.version).isEqualTo("1.16.0")
      assertThat(outdated["six"]?.latestVersion).isEqualTo("1.17.0")
      assertThat(manifest.readText()).isEqualTo(manifestBefore)
      assertThat(lock.readText()).isEqualTo(lockBefore)
    }
  }

  @Test
  fun `external native updates preserve installed workspace dependencies`(): Unit = timeoutRunBlocking(5.minutes) {
    checkExternalNativeUpdates(withLock = true)
  }

  @Test
  fun `external native updates respect strict channel priority`(): Unit = timeoutRunBlocking(5.minutes) {
    checkExternalNativeChannelPriority(disabled = false)
  }

  @Test
  fun `external native updates preserve disabled channel priority`(): Unit = timeoutRunBlocking(5.minutes) {
    checkExternalNativeChannelPriority(disabled = true)
  }

  private suspend fun checkExternalNativeChannelPriority(disabled: Boolean) {
    val high = createNativeChannel(listOf(
      NativeChannelPackage("pixi-priority-shadow", "1.0"),
      NativeChannelPackage("pixi-priority-shadow", "2.0", listOf("__cuda >=99999999")),
      NativeChannelPackage("pixi-priority-control", "1.0"),
      NativeChannelPackage("pixi-priority-control", "2.0"),
    ), "high-channel")
    val low = createNativeChannel(listOf(NativeChannelPackage("pixi-priority-shadow", "2.0")), "low-channel")
    withWorkspace(
      "pixi.toml", environmentFactory, environmentName = "analysis", analysisFeatures = listOf("tools"),
      extraManifest = "[feature.tools]\nchannels = ['${high.toUri()}', '${low.toUri()}']\n",
      beforeManager = { manifest ->
        if (disabled) manifest.writeText(manifest.readText().replace("[workspace]", "[workspace]\nchannel-priority = 'disabled'"))
      },
    ) { interpreter, manager ->
      val executable = checkNotNull(PixiPyTool.getInstance().getCustomExecutablePath(localEel.descriptor))
      val prefix = checkNotNull(interpreter.detectedEnvironment?.pythonHomePath)
      ExecService().execGetStdout(executable, Args(
        "exec", "--spec", "micromamba", "--channel", "conda-forge", "--", "micromamba", "install", "--no-rc", "--yes",
        "--prefix", prefix.pathString, "--channel", high.toUri().toASCIIString(),
        "--", "pixi-priority-shadow=1.0", "pixi-priority-control=1.0",
      )).getOrThrow()
      val manifest = rootFixture.get().resolve("pixi.toml")
      val lock = rootFixture.get().resolve("pixi.lock")
      val beforeManifest = manifest.readBytes()
      val beforeLock = lock.readBytes()
      val beforeInstalled = manager.reloadPackages().getOrThrow().associate { it.name to it.version }
      assertThat(beforeInstalled).containsEntry("pixi-priority-shadow", "1.0").containsEntry("pixi-priority-control", "1.0")

      waitUntilAssertSucceeds("The native update must follow the selected channel priority.") {
        val outdated = manager.listOutdatedPackages()
        assertThat(outdated["pixi-priority-control"]?.latestVersion).isEqualTo("2.0")
        if (disabled) assertThat(outdated["pixi-priority-shadow"]?.latestVersion).isEqualTo("2.0")
        else assertThat(outdated).doesNotContainKey("pixi-priority-shadow")
      }

      assertThat(manager.reloadPackages().getOrThrow().associate { it.name to it.version }).isEqualTo(beforeInstalled)
      assertThat(manifest.readBytes()).isEqualTo(beforeManifest)
      assertThat(lock.readBytes()).isEqualTo(beforeLock)
    }
  }

  @Test
  fun `external native updates work without a lock file`(): Unit = timeoutRunBlocking(5.minutes) {
    checkExternalNativeUpdates(withLock = false)
  }

  @Test
  fun `external native updates can replace external dependencies without changing the prefix`(): Unit = timeoutRunBlocking(5.minutes) {
    val channel = createNativeChannel(listOf(
      NativeChannelPackage("pixi-external-update-probe", "1.0", listOf("pixi-external-update-dependency ==1.0")),
      NativeChannelPackage("pixi-external-update-probe", "2.0", listOf("pixi-external-update-dependency >=2.0")),
      NativeChannelPackage("pixi-external-update-dependency", "1.0"),
      NativeChannelPackage("pixi-external-update-dependency", "2.0"),
    ))
    withWorkspace(
      "pixi.toml", environmentFactory, environmentName = "analysis", analysisFeatures = listOf("tools"),
      extraManifest = "[feature.tools]\nchannels = ['${channel.toUri()}']\n",
    ) { interpreter, manager ->
      val executable = checkNotNull(PixiPyTool.getInstance().getCustomExecutablePath(localEel.descriptor))
      val prefix = checkNotNull(interpreter.detectedEnvironment?.pythonHomePath)
      ExecService().execGetStdout(executable, Args(
        "exec", "--spec", "micromamba", "--channel", "conda-forge", "--", "micromamba", "install", "--no-rc", "--yes",
        "--prefix", prefix.pathString, "--channel", channel.toUri().toASCIIString(), "--", "pixi-external-update-probe=1.0",
      )).getOrThrow()
      val manifest = rootFixture.get().resolve("pixi.toml")
      val lock = rootFixture.get().resolve("pixi.lock")
      val beforeManifest = manifest.readBytes()
      val beforeLock = lock.readBytes()
      val beforeInstalled = manager.reloadPackages().getOrThrow().associate { it.name to it.version }
      assertThat(beforeInstalled).containsEntry("pixi-external-update-probe", "1.0")
        .containsEntry("pixi-external-update-dependency", "1.0")
      fun prefixContents(): Map<String, String> = java.nio.file.Files.walk(prefix).use { paths ->
        paths.filter { java.nio.file.Files.isRegularFile(it) }.toList().associate {
          prefix.relativize(it).pathString to MessageDigest.getInstance("SHA-256").digest(it.readBytes())
            .joinToString("") { byte -> "%02x".format(byte) }
        }
      }

      val beforePrefix = prefixContents()

      waitUntilAssertSucceeds("The external package update can replace an external dependency.") {
        val outdated = manager.listOutdatedPackages()["pixi-external-update-probe"]
        assertThat(outdated?.version).isEqualTo("1.0")
        assertThat(outdated?.latestVersion).isEqualTo("2.0")
      }

      assertThat(manager.reloadPackages().getOrThrow().associate { it.name to it.version }).isEqualTo(beforeInstalled)
      assertThat(prefixContents()).isEqualTo(beforePrefix)
      assertThat(manifest.readBytes()).isEqualTo(beforeManifest)
      assertThat(lock.readBytes()).isEqualTo(beforeLock)
    }
  }

  private suspend fun checkExternalNativeUpdates(withLock: Boolean) {
    val channel = createNativeChannel(listOf(
      NativeChannelPackage("pixi-free-update-probe", "1.0"),
      NativeChannelPackage("pixi-free-update-probe", "2.0"),
      NativeChannelPackage("pixi-blocked-update-probe", "1.0"),
      NativeChannelPackage("pixi-blocked-update-probe", "2.0", listOf("pixi-dependency-probe >=2")),
      NativeChannelPackage("pixi-dependency-probe", "1.0"),
      NativeChannelPackage("pixi-dependency-probe", "2.0"),
    ))
    withWorkspace(
      "pixi.toml", environmentFactory, environmentName = "analysis", analysisFeatures = listOf("tools"),
      extraManifest = "[feature.tools]\nchannels = ['${channel.toUri()}']\n" +
                      "[feature.tools.dependencies]\npixi-dependency-probe = '1.0'\n",
      beforeManager = { manifest ->
        if (!withLock) manifest.parent.resolve("pixi.lock").moveTo(manifest.parent.resolve("saved-pixi.lock"))
      },
    ) { interpreter, manager ->
      val executable = checkNotNull(PixiPyTool.getInstance().getCustomExecutablePath(localEel.descriptor))
      val prefix = checkNotNull(interpreter.detectedEnvironment?.pythonHomePath)
      ExecService().execGetStdout(executable, Args(
        "exec", "--spec", "micromamba", "--channel", "conda-forge", "--", "micromamba", "install", "--no-rc", "--yes",
        "--prefix", prefix.pathString, "--channel", channel.toUri().toASCIIString(),
        "--", "pixi-free-update-probe=1.0", "pixi-blocked-update-probe=1.0",
      )).getOrThrow()
      val manifest = rootFixture.get().resolve("pixi.toml")
      val lock = rootFixture.get().resolve("pixi.lock")
      val beforeManifest = manifest.readBytes()
      val beforeLock = if (withLock) lock.readBytes() else null
      val beforeInstalled = manager.reloadPackages().getOrThrow().associate { it.name to it.version }
      assertThat(beforeInstalled).containsEntry("pixi-free-update-probe", "1.0")
        .containsEntry("pixi-blocked-update-probe", "1.0").containsEntry("pixi-dependency-probe", "1.0")

      waitUntilAssertSucceeds("The independent external package must have a safe native update.") {
        val outdated = manager.listOutdatedPackages()
        assertThat(outdated["pixi-free-update-probe"]?.version).isEqualTo("1.0")
        assertThat(outdated["pixi-free-update-probe"]?.latestVersion).isEqualTo("2.0")
        assertThat(outdated).doesNotContainKey("pixi-blocked-update-probe")
      }

      assertThat(manager.reloadPackages().getOrThrow().associate { it.name to it.version }).isEqualTo(beforeInstalled)
      assertThat(manifest.readBytes()).isEqualTo(beforeManifest)
      if (withLock) assertThat(lock.readBytes()).isEqualTo(beforeLock)
      else assertThat(lock).doesNotExist()
    }
  }

  @Test
  fun `declared native updates work without a lock file`(): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(
      "pixi.toml", environmentFactory, condaDependencies = "six = '1.16.0'",
      beforeManager = { manifest ->
        manifest.writeText(manifest.readText().replace("six = '1.16.0'", "six = '>=1.16.0'"))
        manifest.parent.resolve("pixi.lock").moveTo(manifest.parent.resolve("saved-pixi.lock"))
      },
    ) { _, manager ->
      val manifest = rootFixture.get().resolve("pixi.toml")
      val beforeManifest = manifest.readBytes()
      assertThat(manager.reloadPackages().getOrThrow().single { it.name == "six" }.version).isEqualTo("1.16.0")

      waitUntilAssertSucceeds("The declared native package must have an update without a lock file.") {
        val outdated = manager.listOutdatedPackages()["six"]
        assertThat(outdated?.version).isEqualTo("1.16.0")
        assertThat(outdated?.latestVersion).isEqualTo("1.17.0")
      }

      assertThat(rootFixture.get().resolve("pixi.lock")).doesNotExist()
      assertThat(manifest.readBytes()).isEqualTo(beforeManifest)
    }
  }

  @Test
  fun `Python outdated detection works without a lock file`(): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(
      "pixi.toml", environmentFactory, pypiDependencies = "six = '==1.16.0'",
      beforeManager = { manifest ->
        manifest.parent.resolve("pixi.lock").moveTo(manifest.parent.resolve("saved-pixi.lock"))
      },
    ) { _, manager ->
      val manifest = rootFixture.get().resolve("pixi.toml")
      val beforeManifest = manifest.readBytes()
      assertThat(manager.reloadPackages().getOrThrow().single { it.name == "six" }.version).isEqualTo("1.16.0")

      waitUntilAssertSucceeds("The installed Python package must have an update without a lock file.") {
        val outdated = manager.listOutdatedPackages()["six"]
        assertThat(outdated?.version).isEqualTo("1.16.0")
        assertThat(outdated?.latestVersion).isEqualTo("1.17.0")
      }

      assertThat(rootFixture.get().resolve("pixi.lock")).doesNotExist()
      assertThat(manifest.readBytes()).isEqualTo(beforeManifest)
    }
  }

  @Test
  fun `native outdated detection compares an external downgrade with the locked version`(): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace("pixi.toml", environmentFactory, condaDependencies = "six = '>=1.16.0'") { interpreter, manager ->
      assertThat(manager.reloadPackages().getOrThrow().single { it.name == "six" }.version).isEqualTo("1.17.0")
      val manifest = rootFixture.get().resolve("pixi.toml")
      val lock = rootFixture.get().resolve("pixi.lock")
      val beforeManifest = manifest.readBytes()
      val beforeLock = lock.readBytes()
      val executable = checkNotNull(PixiPyTool.getInstance().getCustomExecutablePath(localEel.descriptor))
      val prefix = checkNotNull(interpreter.detectedEnvironment?.pythonHomePath)
      ExecService().execGetStdout(executable, Args(
        "exec", "--spec", "micromamba", "--channel", "conda-forge", "--", "micromamba", "install", "--no-rc", "--yes",
        "--prefix", prefix.pathString, "--channel", "conda-forge", "--", "six=1.16.0",
      )).getOrThrow()
      assertThat(manager.reloadPackages().getOrThrow().single { it.name == "six" }.version).isEqualTo("1.16.0")
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      assertThat(ExecService().execGetStdout(binary, Args("-c", "import six; print(six.__version__)"))
                   .getOrThrow().trim()).isEqualTo("1.16.0")

      waitUntilAssertSucceeds("The external native downgrade must have the compatible locked update.") {
        val outdated = manager.listOutdatedPackages()["six"]
        assertThat(outdated?.version).isEqualTo("1.16.0")
        assertThat(outdated?.latestVersion).isEqualTo("1.17.0")
      }

      assertThat(manifest.readBytes()).isEqualTo(beforeManifest)
      assertThat(lock.readBytes()).isEqualTo(beforeLock)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `package reads ignore an unsatisfiable foreign platform`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    val foreignPlatform = if (SystemInfo.isWindows) "linux-64" else "win-64"
    withWorkspace(
      manifestName, factory, condaDependencies = "six = '1.16.0'",
      beforeManager = { manifest ->
        manifest.writeText(manifest.readText().replace("six = '1.16.0'", "six = '>=1.16.0'") +
                           "\n[${prefix}target.$foreignPlatform.dependencies]\npython = '9999.*'\n")
      },
    ) { _, manager ->
      val manifest = rootFixture.get().resolve(manifestName)
      val lock = rootFixture.get().resolve("pixi.lock")
      val manifestBefore = manifest.readBytes()
      val lockBefore = lock.readBytes()
      val installed = manager.reloadPackages()
      assertThat(installed.isSuccess).isTrue()
      assertThat(installed.getOrThrow().single { it.name == "six" }.version).isEqualTo("1.16.0")
      val declared = checkNotNull(manager.listDeclaredPackages())
      assertThat(declared.isSuccess).isTrue()
      assertThat(declared.getOrThrow().map { it.name }).contains("six")
      assertThat(manifest.readBytes()).isEqualTo(manifestBefore)
      assertThat(lock.readBytes()).isEqualTo(lockBefore)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `native updates respect a pinned workspace dependency`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, factory, condaDependencies = "six = '1.16.0'", pypiDependencies = "testpath = '==0.5.0'") { _, manager ->
      waitUntilAssertSucceeds("The update check must finish for both package sources.") {
        assertThat(manager.listOutdatedPackages()["testpath"]).isNotNull()
      }
      assertThat(manager.listOutdatedPackages()).doesNotContainKey("six")
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `a batch updates dependencies in their owning features`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    withWorkspace(
      manifestName, factory, environmentName = "analysis", pypiDependencies = "six = '==1.16.0'",
      extraManifest = "[${prefix}feature.tools.pypi-dependencies]\ntestpath = '==0.5.0'\n",
      analysisFeatures = listOf("tools"),
    ) { _, manager ->
      val manifest = rootFixture.get().resolve(manifestName)
      val result = manager.updatePackages(
        PythonRepositoryPackageSpecification(PyPiPackageRepository, "six", "1.17.0"),
        PythonRepositoryPackageSpecification(PyPiPackageRepository, "testpath", "0.6.0"),
      )
      assertThat(result.isSuccess).isTrue()
      val parsed = Toml.parse(manifest.readText())
      assertThat(parsed.getTable("${prefix}pypi-dependencies")?.keySet()).containsExactly("six")
      assertThat(parsed.getTable("${prefix}feature.tools.pypi-dependencies")?.keySet()).containsExactly("testpath")
      val installed = manager.reloadPackages()
      assertThat(installed.isSuccess).isTrue()
      assertThat(installed.getOrThrow().filter { it.name == "six" }).singleElement()
        .extracting { it.version }.isEqualTo("1.17.0")
      assertThat(installed.getOrThrow().filter { it.name == "testpath" }).singleElement()
        .extracting { it.version }.isEqualTo("0.6.0")
    }
  }

  @ParameterizedTest
  @CsvSource("pixi.toml, false", "pyproject.toml, false", "pixi.toml, true", "pyproject.toml, true")
  fun `a compatible batch installs every package`(
    manifestName: String, native: Boolean, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, factory) { _, manager ->
      val repository = if (native) CondaPackageRepository else PyPiPackageRepository
      val secondName = "testpath"
      val secondVersion = "0.6.0"
      val result = manager.updatePackages(
        PythonRepositoryPackageSpecification(repository, "six", "1.17.0"),
        PythonRepositoryPackageSpecification(repository, secondName, secondVersion),
      )
      assertThat(result.isSuccess).isTrue()
      assertThat(result.getOrThrow().filter { it.name == "six" }).describedAs("The installed packages are %s", result.getOrThrow())
        .singleElement()
        .extracting { it.version }.isEqualTo("1.17.0")
      assertThat(result.getOrThrow().filter { it.name == secondName.replace('_', '-') }).singleElement()
        .extracting { it.version }.isEqualTo(secondVersion)
      val declared = manager.listDeclaredPackages()
      assertThat(declared?.isSuccess).isTrue()
      assertThat(declared?.getOrThrow()?.map { it.name }).contains("six", secondName.replace('_', '-'))
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `adding a transitive native dependency preserves its source`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, factory, condaDependencies = "requests = '2.32.3'") { interpreter, manager ->
      val nativeRepository = manager.repositoryManager.repositories.filterIsInstance<PixiNativeRepository>().single()
      val installed = manager.reloadPackages()
      assertThat(installed.isSuccess).isTrue()
      assertThat(installed.getOrThrow().filter { it.name == "urllib3" }).singleElement()
        .extracting { it.defaultRepository }.isSameAs(nativeRepository)
      val before = manager.listDeclaredPackages()
      assertThat(before?.isSuccess).isTrue()
      assertThat(before?.getOrThrow()?.map { it.name }).doesNotContain("urllib3")

      val requirement = checkNotNull(PyRequirementParser.fromLine("urllib3", projectFixture.get()))
      assertThat(manager.addDependencyToFile(requirement)).isTrue()

      val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
      val text = rootFixture.get().resolve(manifestName).readText()
      val manifest = Toml.parse(text)
      assertThat(manifest.getTable("${prefix}dependencies")?.keySet())
        .describedAs("The interpreter is %s at %s. The manifest is %s", interpreter.ref, interpreter.ownerDir, text).contains("urllib3")
      assertThat(declaredPackages(manifestName)).doesNotContain("urllib3")
      val after = manager.listDeclaredPackages()
      assertThat(after?.isSuccess).isTrue()
      assertThat(after?.getOrThrow()?.filter { it.name == "urllib3" }).singleElement()
        .extracting { it.defaultRepository }.isSameAs(nativeRepository)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `changing a transitive native version preserves its original name`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, factory, condaDependencies = "exceptiongroup = '1.3.0'") { interpreter, manager ->
      val installed = manager.reloadPackages()
      assertThat(installed.isSuccess).isTrue()
      val pkg = installed.getOrThrow().single { it.name == "typing-extensions" }
      val declared = manager.listDeclaredPackages()
      assertThat(declared?.isSuccess).isTrue()
      assertThat(declared?.getOrThrow()?.map { it.name }).doesNotContain(pkg.name)

      val updated = PythonPackageManagerUI.forPythonInterpreter(projectFixture.get(), interpreter, errorSink.get())
        .updatePackagesBackground(listOf(PythonRepositoryPackageSpecification(pkg.defaultRepository, pkg.name, "4.14.1")))

      assertThat(errorSink.get().errors).isEmpty()
      assertThat(updated).isNotNull()
      assertThat(updated.orEmpty().filter { it.name == pkg.name }).singleElement()
        .extracting { it.version }.isEqualTo("4.14.1")
      val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
      val manifest = Toml.parse(rootFixture.get().resolve(manifestName).readText())
      assertThat(manifest.getTable("${prefix}dependencies")?.keySet())
        .contains("typing_extensions").doesNotContain("typing-extensions")
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `native version details work without a Conda cache`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, factory, condaDependencies = "typing_extensions = '4.14.1'") { interpreter, manager ->
      val nativeRepository = manager.repositoryManager.repositories.filterIsInstance<PixiNativeRepository>().single()
      val details = nativeRepository.buildPackageDetails("typing-extensions")
      assertThat(details.isSuccess).isTrue()
      assertThat(details.getOrThrow().name).isEqualTo("typing_extensions")
      assertThat(details.getOrThrow().availableVersions).contains("4.14.1", "4.15.0")
      assertThat(manager.repositoryManager.getVersions("typing-extensions", nativeRepository)).contains("4.15.0")
      val specification = details.getOrThrow().toPackageSpecification("4.15.0")
      assertThat(specification).isNotNull()
      val updated = PythonPackageManagerUI.forPythonInterpreter(projectFixture.get(), interpreter, errorSink.get())
        .updatePackagesBackground(listOf(checkNotNull(specification)))
      assertThat(errorSink.get().errors).isEmpty()
      assertThat(updated).isNotNull()
      assertThat(updated.orEmpty().filter { it.name == "typing-extensions" }).singleElement()
        .extracting { it.version }.isEqualTo("4.15.0")
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `the default environment can exclude the default feature`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    withWorkspace(
      manifestName, factory,
      defaultEnvironmentDefinition = "{ features = ['tools'], no-default-feature = true }",
      extraManifest = "[${prefix}feature.tools.dependencies]\npython = '3.13.*'\n",
    ) { interpreter, _ ->
      val installed = PythonPackageManagerUI.forPythonInterpreter(projectFixture.get(), interpreter, errorSink.get())
        .installPackageBackground("six", pyRequirementVersionSpec("1.17.0"))
      assertThat(errorSink.get().errors).isEmpty()
      assertThat(installed).isNotNull()
      assertThat(installed.orEmpty().filter { it.name == "six" }).singleElement()
        .extracting { it.version }.isEqualTo("1.17.0")
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val imported = ExecService().execGetStdout(binary, Args("-c", "import six; print(six.__version__)"))
      assertThat(imported.isSuccess).isTrue()
      assertThat(imported.getOrThrow().trim()).isEqualTo("1.17.0")
      assertThat(declaredPackages(manifestName)).isEmpty()
      val manifest = Toml.parse(rootFixture.get().resolve(manifestName).readText())
      assertThat(manifest.getTable("${prefix}environments.default.pypi-dependencies")?.keySet()).containsExactly("six")
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `a failed batch preserves the manifest and lock`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, factory) { interpreter, manager ->
      val manifest = rootFixture.get().resolve(manifestName)
      val lock = rootFixture.get().resolve("pixi.lock")
      val manifestBefore = manifest.readText()
      val lockBefore = lock.readText()
      val result = manager.updatePackages(
        PythonRepositoryPackageSpecification(PyPiPackageRepository, "six", "1.17.0"),
        PythonRepositoryPackageSpecification(PyPiPackageRepository, "testpath", "999999.0"),
      )
      assertThat(result.isSuccess).isFalse()
      assertThat(manifest.readText()).isEqualTo(manifestBefore)
      assertThat(lock.readText()).isEqualTo(lockBefore)
      val packages = manager.reloadPackages()
      assertThat(packages.isSuccess).isTrue()
      assertThat(packages.getOrThrow().map { it.name }).doesNotContain("six", "testpath")
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val absent = ExecService().execGetStdout(binary, Args("-c", "import importlib.util; print(importlib.util.find_spec('six') is None)"))
      assertThat(absent.isSuccess).isTrue()
      assertThat(absent.getOrThrow().trim()).isEqualTo("True")
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `dependency discovery supports caching and adding a requirement`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, factory) { _, manager ->
      assertThat(manager.getRootDependenciesFile()?.virtualFile?.name).isEqualTo(manifestName)
      val before = manager.listDeclaredPackagesCached()
      assertThat(before?.isSuccess).isTrue()
      assertThat(before?.getOrThrow()?.map { it.name }).contains("python").doesNotContain("six")

      val requirement = checkNotNull(PyRequirementParser.fromLine("six==1.17.0", projectFixture.get()))
      assertThat(manager.addDependencyToFile(requirement)).isTrue()
      assertThat(declaredPackages(manifestName)).containsExactly("six")
      val after = manager.listDeclaredPackagesCached()
      assertThat(after?.isSuccess).isTrue()
      assertThat(after?.getOrThrow()?.filter { it.name == "six" }).singleElement()
        .extracting { it.version }.isEqualTo("1.17.0")
    }
  }

  @ParameterizedTest
  @CsvSource("pixi.toml, UPDATE", "pyproject.toml, UPDATE", "pixi.toml, REMOVE", "pyproject.toml, REMOVE")
  fun `native package changes preserve the Conda name`(
    manifestName: String, change: DependencyChange, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, factory, condaDependencies = "typing_extensions = '4.14.1'") { interpreter, manager ->
      val nativeRepository = manager.repositoryManager.repositories.filterIsInstance<PixiNativeRepository>().single()
      val installed = manager.reloadPackages()
      assertThat(installed.isSuccess).isTrue()
      val nativePackage = installed.getOrThrow().single { it.name == "typing-extensions" }
      assertThat(nativePackage.defaultRepository).isSameAs(nativeRepository)

      val ui = PythonPackageManagerUI.forPythonInterpreter(projectFixture.get(), interpreter, errorSink.get())
      val result = when (change) {
        DependencyChange.UPDATE -> ui.updatePackagesBackground(
          listOf(PythonRepositoryPackageSpecification(nativeRepository, nativePackage.name, "4.15.0")),
        )
        DependencyChange.REMOVE -> ui.uninstallPackagesBackground(listOf(nativePackage.name))
      }

      assertThat(errorSink.get().errors).isEmpty()
      assertThat(result).isNotNull()
      val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
      val manifest = Toml.parse(rootFixture.get().resolve(manifestName).readText())
      when (change) {
        DependencyChange.UPDATE -> {
          assertThat(result.orEmpty().filter { it.name == "typing-extensions" }).singleElement()
            .extracting { it.version }.isEqualTo("4.15.0")
          assertThat(manifest.getTable("${prefix}dependencies")?.keySet())
            .contains("typing_extensions").doesNotContain("typing-extensions")
        }
        DependencyChange.REMOVE -> {
          assertThat(result.orEmpty().map { it.name }).doesNotContain("typing-extensions")
          assertThat(manifest.getTable("${prefix}dependencies")?.keySet()).doesNotContain("typing_extensions")
        }
      }
    }
  }

  @ParameterizedTest
  @CsvSource(
    "pixi.toml, default, UPDATE, 0.78.0", "pyproject.toml, default, UPDATE, 0.78.0",
    "pixi.toml, tools, UPDATE, 0.78.0", "pyproject.toml, tools, UPDATE, 0.78.0",
    "pixi.toml, default, REMOVE, 0.78.0", "pyproject.toml, default, REMOVE, 0.78.0",
    "pixi.toml, tools, REMOVE, 0.78.0", "pyproject.toml, tools, REMOVE, 0.78.0",
    "pixi.toml, tools, REMOVE, 0.81.0", "pyproject.toml, tools, REMOVE, 0.81.0",
  )
  fun `editing an inherited dependency changes its owning feature`(
    manifestName: String, feature: String, change: DependencyChange, pixiVersion: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    withWorkspace(
      manifestName, factory, environmentName = "analysis",
      pypiDependencies = if (feature == "default") "six = '==1.16.0'" else "",
      extraManifest = if (feature == "default") "" else "[${prefix}feature.tools.pypi-dependencies]\nsix = '==1.16.0'\n",
      analysisFeatures = if (feature == "default") emptyList() else listOf("tools"),
      pixiVersion = pixiVersion,
    ) { interpreter, manager ->
      val before = manager.reloadPackages()
      assertThat(before.isSuccess).isTrue()
      assertThat(before.getOrThrow().filter { it.name == "six" }).singleElement()
        .extracting { it.version }.isEqualTo("1.16.0")
      val ui = PythonPackageManagerUI.forPythonInterpreter(projectFixture.get(), interpreter, errorSink.get())
      val result = when (change) {
        DependencyChange.UPDATE -> ui.updatePackagesBackground(
          listOf(PythonRepositoryPackageSpecification(PyPiPackageRepository, "six", "1.17.0")),
        )
        DependencyChange.REMOVE -> ui.uninstallPackagesBackground(listOf("six"))
      }
      assertThat(errorSink.get().errors).isEmpty()
      assertThat(result).isNotNull()
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      when (change) {
        DependencyChange.UPDATE -> {
          assertThat(result.orEmpty().filter { it.name == "six" }).singleElement()
            .extracting { it.version }.isEqualTo("1.17.0")
          val imported = ExecService().execGetStdout(binary, Args("-c", "import six; print(six.__version__)"))
          assertThat(imported.isSuccess).isTrue()
          assertThat(imported.getOrThrow().trim()).isEqualTo("1.17.0")
        }
        DependencyChange.REMOVE -> {
          assertThat(result.orEmpty().map { it.name }).doesNotContain("six")
          val absent =
            ExecService().execGetStdout(binary, Args("-c", "import importlib.util; print(importlib.util.find_spec('six') is None)"))
          assertThat(absent.isSuccess).isTrue()
          assertThat(absent.getOrThrow().trim()).isEqualTo("True")
          assertThat(declaredPackages(manifestName)).doesNotContain("six")
          val parsed = Toml.parse(rootFixture.get().resolve(manifestName).readText())
          assertThat(parsed.getTable("${prefix}feature.tools.pypi-dependencies")?.keySet().orEmpty()).doesNotContain("six")
          assertThat(parsed.getArray("dependency-groups.tools")?.toList().orEmpty()).isEmpty()
        }
      }
      val parsed = Toml.parse(rootFixture.get().resolve(manifestName).readText())
      assertThat(parsed.getTable("${prefix}environments.analysis.pypi-dependencies")?.keySet().orEmpty()).isEmpty()
      assertThat(rootFixture.get().resolve(manifestName).readText()).startsWith("# Keep the workspace comment.\n")
    }
  }

  enum class DependencyChange { UPDATE, REMOVE }

  @ParameterizedTest
  @CsvSource("pixi.toml, 0.78.0", "pyproject.toml, 0.78.0", "pixi.toml, 0.81.0", "pyproject.toml, 0.81.0")
  fun `the package manager lists an editable workspace package with an indexed dependency`(
    manifestName: String, pixiVersion: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(
      manifestName, factory,
      pypiDependencies = """
        app = { path = "packages/app", editable = true }
        testpath = { version = ">=0.6.0, <0.7", index = "https://pypi.org/simple/" }
      """.trimIndent(),
      pixiVersion = pixiVersion,
      workspacePackage = true,
    ) { interpreter, manager ->
      val packages = manager.reloadPackages()
      assertThat(packages.isSuccess).describedAs("List packages with an explicit PyPI index").isTrue()
      assertThat(packages.getOrThrow().filter { it.name == "testpath" }).singleElement()
        .extracting { it.version }.isEqualTo("0.6.0")
      assertThat(packages.getOrThrow().filter { it.name == "app" }).hasSize(1)
      val declared = manager.listDeclaredPackages()
      assertThat(declared?.isSuccess).isTrue()
      assertThat(declared?.getOrThrow()?.map { it.name }).contains("testpath", "app")
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val imported = ExecService().execGetStdout(binary, Args("-c", "import app, testpath; print(app.VALUE, testpath.__version__)"))
      assertThat(imported.isSuccess).isTrue()
      assertThat(imported.getOrThrow().trim()).isEqualTo("42 0.6.0")
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `the package manager lists a fresh workspace environment`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, factory) { interpreter, manager ->
      val packages = manager.reloadPackages()
      assertThat(packages.isSuccess).describedAs("List packages without requiring pip in the Pixi environment").isTrue()
      assertThat(packages.getOrThrow().map { it.name }).doesNotContain("six")
      assertThat(packages.getOrThrow().map { it.name }).contains("python")
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val pip = ExecService().execGetStdout(binary, Args("-c", "import importlib.util; print(importlib.util.find_spec('pip') is None)"))
      assertThat(pip.isSuccess).isTrue()
      assertThat(pip.getOrThrow().trim()).isEqualTo("True")
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `the package manager installs and records a workspace dependency`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, factory) { interpreter, manager ->
      val errors = errorSink.get()
      assertThat(manager.listInstalledPackages().map { it.name }).doesNotContain("six")

      val packages = PythonPackageManagerUI.forPythonInterpreter(projectFixture.get(), interpreter, errors)
        .installPackageBackground("six", pyRequirementVersionSpec("1.17.0"))

      assertThat(errors.errors).isEmpty()
      assertThat(packages).isNotNull()
      assertThat(packages.orEmpty().filter { it.name == "six" }).singleElement()
        .extracting { it.version }.isEqualTo("1.17.0")
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val imported = ExecService().execGetStdout(binary, Args("-c", "import six; print(six.__version__)"))
      assertThat(imported.isSuccess).isTrue()
      assertThat(imported.getOrThrow().trim()).isEqualTo("1.17.0")
      val manifest = rootFixture.get().resolve(manifestName)
      assertThat(declaredPackages(manifestName)).containsExactly("six")
      val declared = manager.listDeclaredPackages()
      assertThat(declared?.isSuccess).isTrue()
      assertThat(declared?.getOrThrow()?.map { it.name }).contains("six", "python")
      assertThat(manifest.readText()).startsWith("# Keep the workspace comment.\n")
    }
  }

  // The version change and removal form one dependency lifecycle.
  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `the package manager updates and removes a declared dependency`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, factory) { interpreter, manager ->
      val ui = PythonPackageManagerUI.forPythonInterpreter(projectFixture.get(), interpreter, errorSink.get())
      val installed = ui.installPackageBackground("six", pyRequirementVersionSpec("1.16.0"))
      assertThat(installed.orEmpty().filter { it.name == "six" }).singleElement().extracting { it.version }.isEqualTo("1.16.0")

      val updated = ui.updatePackagesBackground(listOf(PythonRepositoryPackageSpecification(PyPiPackageRepository, "six", "1.17.0")))
      assertThat(updated.orEmpty().filter { it.name == "six" }).singleElement().extracting { it.version }.isEqualTo("1.17.0")
      assertThat(declaredPackages(manifestName)).containsExactly("six")
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val imported = ExecService().execGetStdout(binary, Args("-c", "import six; print(six.__version__)"))
      assertThat(imported.isSuccess).isTrue()
      assertThat(imported.getOrThrow().trim()).isEqualTo("1.17.0")

      val removed = ui.uninstallPackagesBackground(listOf("six"))
      assertThat(removed).isNotNull()
      assertThat(removed.orEmpty().map { it.name }).doesNotContain("six")
      assertThat(declaredPackages(manifestName)).isEmpty()
      val absent = ExecService().execGetStdout(binary, Args("-c", "import importlib.util; print(importlib.util.find_spec('six') is None)"))
      assertThat(absent.isSuccess).isTrue()
      assertThat(absent.getOrThrow().trim()).isEqualTo("True")
      assertThat(errorSink.get().errors).isEmpty()
      val packages = manager.reloadPackages()
      assertThat(packages.isSuccess).isTrue()
      assertThat(packages.getOrThrow().map { it.name }).doesNotContain("six")
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `a failed installation preserves the manifest`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, factory) { _, manager ->
      val manifest = rootFixture.get().resolve(manifestName)
      val before = manifest.readText()
      val request = PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(
        listOf(PythonRepositoryPackageSpecification(PyPiPackageRepository, "six", "999999.0")),
      )
      val result = manager.installPackageCommand(request, emptyList())
      assertThat(result.isSuccess).isFalse()
      assertThat(manifest.readText()).isEqualTo(before)
      val packages = manager.reloadPackages()
      assertThat(packages.isSuccess).isTrue()
      assertThat(packages.getOrThrow().map { it.name }).doesNotContain("six")
    }
  }

  private data class NativeChannelPackage(val name: String, val version: String, val dependencies: List<String> = emptyList())

  private fun createNativeChannel(vararg names: String): java.nio.file.Path =
    createNativeChannel(names.map { NativeChannelPackage(it, "1.2.3") })

  private fun createNativeChannel(
    packages: List<NativeChannelPackage>, directoryName: String = "private-channel",
  ): java.nio.file.Path {
    val channel = rootFixture.get().resolve(directoryName).createDirectories()
    val records = packages.map { (name, version, dependencies) ->
      val index = """
        {"name":"$name","version":"$version","build":"0","build_number":0,
         "subdir":"noarch","noarch":"generic","depends":[${dependencies.joinToString { "\"$it\"" }}]}
      """.trimIndent()
      val archive = channel.resolve("noarch").createDirectories().resolve("$name-$version-0.tar.bz2")
      Compressor.Tar(archive, Compressor.Tar.Compression.BZIP2).use {
        it.addFile("info/index.json", index.byteInputStream(), 0)
        it.addFile("info/files", "".byteInputStream(), 0)
      }
      val bytes = archive.readBytes()
      val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
      "\"$name-$version-0.tar.bz2\": ${index.dropLast(1)},\"size\":${bytes.size},\"sha256\":\"$sha256\"}"
    }
    for (platform in listOf("noarch", "linux-64", "linux-aarch64", "osx-arm64", "osx-64", "win-64")) {
      val platformPackages = if (platform == "noarch") records.joinToString(",") else ""
      channel.resolve(platform).createDirectories().resolve("repodata.json").writeText(
        """{"info":{"subdir":"$platform"},"packages":{$platformPackages},"packages.conda":{},"repodata_version":1}""",
      )
    }
    return channel
  }

  private fun declaredPackages(manifestName: String): Set<String> {
    val parsed = Toml.parse(rootFixture.get().resolve(manifestName).readText())
    assertThat(parsed.errors()).isEmpty()
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    return parsed.getTable("${prefix}pypi-dependencies")?.keySet().orEmpty() +
           parsed.getArray("project.dependencies")?.toList().orEmpty().mapNotNull {
             PyRequirementParser.fromLine(it as String, projectFixture.get())?.name
           }
  }

  @ParameterizedTest
  @ValueSource(strings = ["pixi.toml", "pyproject.toml"])
  fun `an installation into a named environment does not change the default environment`(
    manifestName: String, factory: PyEnvironmentFactory,
  ): Unit = timeoutRunBlocking(5.minutes) {
    withWorkspace(manifestName, factory, environmentName = "analysis") { interpreter, _ ->
      val packages = PythonPackageManagerUI.forPythonInterpreter(projectFixture.get(), interpreter, errorSink.get())
        .installPackageBackground("six", pyRequirementVersionSpec("1.17.0"))
      assertThat(errorSink.get().errors).isEmpty()
      assertThat(packages.orEmpty().filter { it.name == "six" }).singleElement().extracting { it.version }.isEqualTo("1.17.0")
      val binary = checkNotNull(interpreter.detectedEnvironment?.pythonBinaryPath)
      val imported = ExecService().execGetStdout(binary, Args("-c", "import six; print(six.__version__)"))
      assertThat(imported.isSuccess).isTrue()
      assertThat(imported.getOrThrow().trim()).isEqualTo("1.17.0")
      assertThat(declaredPackages(manifestName)).isEmpty()
      val defaultInterpreter = checkNotNull(pyProjectFixture.get().getInterpreter())
      val defaultManager = PythonPackageManager.forPythonInterpreter(projectFixture.get(), defaultInterpreter)
      val defaultPackages = defaultManager.reloadPackages()
      assertThat(defaultPackages.isSuccess).isTrue()
      assertThat(defaultPackages.getOrThrow().map { it.name }).doesNotContain("six")
      val defaultBinary = checkNotNull(defaultInterpreter.detectedEnvironment?.pythonBinaryPath)
      val absent =
        ExecService().execGetStdout(defaultBinary, Args("-c", "import importlib.util; print(importlib.util.find_spec('six') is None)"))
      assertThat(absent.isSuccess).isTrue()
      assertThat(absent.getOrThrow().trim()).isEqualTo("True")
    }
  }

  private suspend fun withWorkspace(
    manifestName: String,
    factory: PyEnvironmentFactory,
    environmentName: String = "default",
    pypiDependencies: String = "",
    pixiVersion: String = PIXI_TEST_VERSION,
    workspacePackage: Boolean = false,
    condaDependencies: String = "",
    extraManifest: String = "",
    analysisFeatures: List<String> = emptyList(),
    defaultEnvironmentDefinition: String? = null,
    includeAnalysisEnvironment: Boolean = true,
    beforeManager: (java.nio.file.Path) -> Unit = {},
    action: suspend (PythonInterpreter, PythonPackageManager) -> Unit,
  ) {
    val root = rootFixture.get()
    val prefix = if (manifestName == "pyproject.toml") "tool.pixi." else ""
    val manifest = root.resolve(manifestName)
    if (workspacePackage) {
      val member = root.resolve("packages/app").createDirectories()
      member.resolve("pyproject.toml").writeText("""
        [project]
        name = "app"
        version = "0.1.0"
        dependencies = []
        [build-system]
        requires = ["hatchling"]
        build-backend = "hatchling.build"
      """.trimIndent())
      member.resolve("src/app").createDirectories().resolve("__init__.py").writeText("VALUE = 42\n")
    }
    manifest.writeText("""
      # Keep the workspace comment.
      [${prefix}workspace]
      channels = ['conda-forge']
      platforms = ['linux-64', 'linux-aarch64', 'osx-arm64', 'osx-64', 'win-64']
      [${prefix}dependencies]
      python = '3.13.*'
      $condaDependencies
      [${prefix}environments]
    """.trimIndent() +
                       (if (includeAnalysisEnvironment) "\nanalysis = [${analysisFeatures.joinToString { "'$it'" }}]\n" else "\n") +
                       (defaultEnvironmentDefinition?.let { "\ndefault = $it\n" } ?: "") +
                       (if (pypiDependencies.isEmpty()) "" else "\n[${prefix}pypi-dependencies]\n$pypiDependencies\n") +
                       "\n$extraManifest")
    if (manifestName == "pyproject.toml") {
      manifest.writeText(manifest.readText() + "\n[project]\nname = 'packaging-test'\nversion = '0.1.0'\n")
    }
    val environment = checkNotNull(factory.createEnvironment(pixiEnvironment(pixiVersion) {
      pythonVersion = pythonVersion("3.13")
    }).unwrap<PixiPyEnvironment>())
    val tool = PixiPyTool.getInstance()
    val previousExecutable = tool.getCustomExecutablePath(localEel.descriptor)
    tool.setCustomExecutablePath(localEel.descriptor, environment.pixiExecutable)
    val pyProject = pyProjectFixture.get()
    val registry = PythonInterpreterRegistry.getInstance(projectFixture.get())
    val errors = errorSink.get()
    try {
      assertThat(pyProject.configureSdkIfNeeded()).isInstanceOf(InterpreterConfigurationResult.Configured::class.java)
      val interpreter = if (environmentName == "default") checkNotNull(pyProject.getInterpreter())
      else {
        val installed = ExecService().execGetStdout(environment.pixiExecutable, Args(
          "install", "--manifest-path", manifest.pathString, "--environment", environmentName,
        ))
        assertThat(installed.isSuccess).isTrue()
        val created = registry.newPythonInterpreter(pyProject, PyInterpreterRef.native(tool.fusId, PyEnvRef(environmentName)))
        assertThat(created.isSuccess).isTrue()
        registry.registerPythonInterpreter(created.getOrThrow())
      }
      beforeManager(manifest)
      val manager = PythonPackageManager.forPythonInterpreter(projectFixture.get(), interpreter)
      assertThat(manager).isInstanceOf(PixiPackageManager::class.java)
      action(interpreter, manager)
    }
    finally {
      errors.clear()
      for (interpreter in registry.interpreters(pyProject)) registry.removePythonInterpreter(pyProject, interpreter)
      tool.setCustomExecutablePath(localEel.descriptor, previousExecutable)
    }
  }
}
