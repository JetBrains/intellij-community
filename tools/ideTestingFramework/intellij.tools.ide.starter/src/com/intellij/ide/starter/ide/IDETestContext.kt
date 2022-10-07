package com.intellij.ide.starter.ide

import com.intellij.ide.starter.buildTool.BuildToolProvider
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.ide.command.MarshallableCommand
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.models.andThen
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.profiler.ProfilerType
import com.intellij.ide.starter.report.publisher.ReportPublisher
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.logOutput
import org.kodein.di.direct
import org.kodein.di.factory
import org.kodein.di.instance
import org.kodein.di.newInstance
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.io.path.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class IDETestContext(
  val paths: IDEDataPaths,
  val ide: InstalledIde,
  val testCase: TestCase,
  val testName: String,
  private val _resolvedProjectHome: Path?,
  var patchVMOptions: VMOptions.() -> VMOptions,
  val ciServer: CIServer,
  var profilerType: ProfilerType = ProfilerType.NONE,
  val publishers: List<ReportPublisher> = di.direct.instance(),
  var isReportPublishingEnabled: Boolean = false
) {
  companion object {
    const val OPENTELEMETRY_FILE = "opentelemetry.json"
  }

  val resolvedProjectHome: Path
    get() = checkNotNull(_resolvedProjectHome) { "Project is not found for the test $testName" }

  val pluginConfigurator: PluginConfigurator by di.newInstance { factory<IDETestContext, PluginConfigurator>().invoke(this@IDETestContext) }

  val buildTools: BuildToolProvider by di.newInstance { factory<IDETestContext, BuildToolProvider>().invoke(this@IDETestContext) }

  fun addVMOptionsPatch(patchVMOptions: VMOptions.() -> VMOptions): IDETestContext {
    this.patchVMOptions = this.patchVMOptions.andThen(patchVMOptions)
    return this
  }

  fun addLockFileForUITest(fileName: String): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("uiLockTempFile", paths.tempDir / fileName)
    }

  fun disableLinuxNativeMenuForce(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("linux.native.menu.force.disable", true)
    }

  fun setMemorySize(sizeMb: Int): IDETestContext =
    addVMOptionsPatch {
      this
        .withXmx(sizeMb)
    }

  fun disableGitLogIndexing(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("vcs.log.index.git", false)
    }

  fun executeAfterProjectOpening(executeAfterProjectOpening: Boolean = true) = addVMOptionsPatch {
    addSystemProperty("performance.execute.script.after.project.opened", executeAfterProjectOpening)
  }

  fun executeDuringIndexing(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("performance.execute.script.after.scanning", true)
    }

  fun withGtk2OnLinux(): IDETestContext =
    addVMOptionsPatch {
      this
        .addSystemProperty("jdk.gtk.verbose", true)
        .let {
          // Desperate attempt to fix JBR-2783
          if (SystemInfo.isLinux) {
            it.addSystemProperty("jdk.gtk.version", 2)
          }
          else {
            it
          }
        }
    }

  fun disableInstantIdeShutdown(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("ide.instant.shutdown", false)
    }

  fun enableSlowOperationsInEdtInTests(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("ide.slow.operations.assertion", false)
    }

  /**
   * Does not allow IDE to fork a process that sends FUS statistics on IDE shutdown.
   * On Windows that forked process may prevent some files from removing.
   * See [com.intellij.internal.statistic.EventLogApplicationLifecycleListener]
   */
  fun disableFusSendingOnIdeClose(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("feature.usage.event.log.send.on.ide.close", false)
    }

  fun withVerboseIndexingDiagnostics(dumpPaths: Boolean = false): IDETestContext =
    addVMOptionsPatch {
      this
        .addSystemProperty("intellij.indexes.diagnostics.should.dump.for.interrupted.index.updaters", true)
        .addSystemProperty("intellij.indexes.diagnostics.limit.of.files", 10000)
        .addSystemProperty("intellij.indexes.diagnostics.should.dump.paths.of.indexed.files", dumpPaths)
        // Dumping of lists of indexed file paths may require a lot of memory.
        .withXmx(4 * 1024)
    }

  fun setPathForMemorySnapshot(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("memory.snapshots.path", paths.logsDir)
    }

  fun setPathForSnapshots(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("snapshots.path", paths.snapshotsDir)
    }

  @Suppress("unused")
  fun collectMemorySnapshotOnFailedPluginUnload(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("ide.plugins.snapshot.on.unload.fail", true)
    }

  fun setPerProjectDynamicPluginsFlag(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("ide.plugins.per.project", true)
    }

  // seems, doesn't work for Maven
  fun disableAutoImport(disabled: Boolean = true) = addVMOptionsPatch {
    addSystemProperty("external.system.auto.import.disabled", disabled)
  }

  fun disableOrdinaryIndexes() = addVMOptionsPatch {
    addSystemProperty("idea.use.only.index.infrastructure.extension", true)
  }

  fun setSharedIndexesDownload(enable: Boolean = true) = addVMOptionsPatch {
    addSystemProperty("shared.indexes.bundled", enable)
      .addSystemProperty("shared.indexes.download", enable)
      .addSystemProperty("shared.indexes.download.auto.consent", enable)
  }

  fun skipIndicesInitialization(value: Boolean = true) = addVMOptionsPatch {
    addSystemProperty("idea.skip.indices.initialization", value)
  }

  fun enableAsyncProfiler() = addVMOptionsPatch {
    addSystemProperty("integrationTests.profiler", "async")
  }

  fun doRefreshAfterJpsLibraryDownloaded(value: Boolean = true) = addVMOptionsPatch {
    addSystemProperty("idea.do.refresh.after.jps.library.downloaded", value)
  }

  fun collectImportProjectPerfMetrics() = addVMOptionsPatch {
    addSystemProperty("idea.collect.project.import.performance", true)
  }

  fun collectOpenTelemetry() = addVMOptionsPatch {
    addSystemProperty("idea.diagnostic.opentelemetry.file", paths.logsDir.resolve(OPENTELEMETRY_FILE))
  }

  fun enableVerboseOpenTelemetry() = addVMOptionsPatch {
    addSystemProperty("idea.diagnostic.opentelemetry.verbose", true)
  }

  fun enableWorkspaceModelVerboseLogs() = addVMOptionsPatch {
    configureLoggers(traceLoggers = listOf("com.intellij.workspaceModel"))
  }

  fun wipeSystemDir() = apply {
    //TODO: it would be better to allocate a new context instead of wiping the folder
    logOutput("Cleaning system dir for $this at $paths")
    paths.systemDir.toFile().deleteRecursively()
  }

  fun wipeLogsDir() = apply {
    //TODO: it would be better to allocate a new context instead of wiping the folder
    logOutput("Cleaning logs dir for $this at $paths")
    paths.logsDir.toFile().deleteRecursively()
  }

  fun wipeReportDir() = apply {
    logOutput("Cleaning report dir for $this at $paths")
    Files.walk(paths.reportsDir)
      .filter { Files.isRegularFile(it) }
      .map { it.toFile() }
      .forEach { it.delete() }
  }

  fun wipeProjectsDir() = apply {
    val path = paths.systemDir / "projects"
    logOutput("Cleaning project cache dir for $this at $path")
    path.toFile().deleteRecursively()
  }

  fun wipeEventLogDataDir() = apply {
    val path = paths.systemDir / "event-log-data"
    logOutput("Cleaning event-log-data dir for $this at $path")
    path.toFile().deleteRecursively()
  }

  fun wipeSnapshotDir() = apply {
    val path = paths.snapshotsDir
    logOutput("Cleaning snapshot dir for $this at $path")
    path.toFile().deleteRecursively()
  }

  fun runContext(
    patchVMOptions: VMOptions.() -> VMOptions = { this },
    commandLine: IDECommandLine? = null,
    commands: Iterable<MarshallableCommand> = CommandChain(),
    codeBuilder: (CodeInjector.() -> Unit)? = null,
    runTimeout: Duration = 10.minutes,
    useStartupScript: Boolean = true,
    launchName: String = "",
    expectedKill: Boolean = false,
    collectNativeThreads: Boolean = false
  ): IDERunContext {
    return IDERunContext(testContext = this)
      .copy(
        commandLine = commandLine,
        commands = commands,
        codeBuilder = codeBuilder,
        runTimeout = runTimeout,
        useStartupScript = useStartupScript,
        launchName = launchName,
        expectedKill = expectedKill,
        collectNativeThreads = collectNativeThreads
      )
      .addVMOptionsPatch(patchVMOptions)
  }

  /**
   * Setup profiler injection
   */
  fun setProfiler(profilerType: ProfilerType): IDETestContext {
    this.profilerType = profilerType
    return this
  }

  fun internalMode(value: Boolean = true) = addVMOptionsPatch { addSystemProperty("idea.is.internal", value) }

  /**
   * Cleans .idea and removes all the .iml files for project
   */
  fun prepareProjectCleanImport(): IDETestContext {
    return removeIdeaProjectDirectory().removeAllImlFilesInProject()
  }

  fun disableAutoSetupJavaProject() = addVMOptionsPatch {
    addSystemProperty("idea.java.project.setup.disabled", true)
  }

  fun disablePackageSearchBuildFiles() = addVMOptionsPatch {
    addSystemProperty("idea.pkgs.disableLoading", true)
  }

  fun removeIdeaProjectDirectory(): IDETestContext {
    val ideaDirPath = resolvedProjectHome.resolve(".idea")

    logOutput("Removing $ideaDirPath ...")

    if (ideaDirPath.notExists()) {
      logOutput("Idea project directory $ideaDirPath doesn't exist. So, it will not be deleted")
      return this
    }

    ideaDirPath.toFile().deleteRecursively()
    return this
  }

  fun removeAllImlFilesInProject(): IDETestContext {
    val projectDir = resolvedProjectHome

    logOutput("Removing all .iml files in $projectDir ...")

    projectDir.toFile().walkTopDown()
      .forEach {
        if (it.isFile && it.extension == "iml") {
          it.delete()
          logOutput("File ${it.path} is deleted")
        }
      }

    return this
  }

  fun runIDE(
    patchVMOptions: VMOptions.() -> VMOptions = { this },
    commandLine: IDECommandLine? = null,
    commands: Iterable<MarshallableCommand>,
    codeBuilder: (CodeInjector.() -> Unit)? = null,
    runTimeout: Duration = 10.minutes,
    useStartupScript: Boolean = true,
    launchName: String = "",
    expectedKill: Boolean = false,
    collectNativeThreads: Boolean = false
  ): IDEStartResult {

    val ideRunResult = runContext(
      commandLine = commandLine,
      commands = commands,
      codeBuilder = codeBuilder,
      runTimeout = runTimeout,
      useStartupScript = useStartupScript,
      launchName = launchName,
      expectedKill = expectedKill,
      collectNativeThreads = collectNativeThreads,
      patchVMOptions = patchVMOptions
    ).runIDE()

    if (isReportPublishingEnabled) publishers.forEach {
      it.publish(ideRunResult)
    }
    if (ideRunResult.failureError != null) throw ideRunResult.failureError
    return ideRunResult
  }

  fun warmUp(
    patchVMOptions: VMOptions.() -> VMOptions = { this },
    commands: Iterable<MarshallableCommand>,
    runTimeout: Duration = 10.minutes,
    storeClassReport: Boolean = false
  ): IDEStartResult {
    val updatedContext = this.copy(testName = "${this.testName}/warmup")
    val result = updatedContext.runIDE(
      patchVMOptions = {
        this.run {
          if (storeClassReport) {
            this.enableClassLoadingReport(paths.reportsDir / "class-report.txt")
          }
          else {
            this
          }
        }.patchVMOptions()
      },
      commands = testCase.commands.plus(commands),
      runTimeout = runTimeout
    )
    updatedContext.publishArtifact(this.paths.reportsDir)
    return result
  }

  fun removeAndUnpackProject(): IDETestContext {
    testCase.markNotReusable().projectInfo?.downloadAndUnpackProject()
    return this
  }

  fun setProviderMemoryOnlyOnLinux(): IDETestContext {
    if (SystemInfo.isLinux) {
      val optionsConfig = paths.configDir.resolve("options")
      optionsConfig.toFile().mkdirs()
      val securityXml = optionsConfig.resolve("security.xml")
      securityXml.toFile().createNewFile()
      securityXml.toFile().writeText("""<application>
  <component name="PasswordSafe">
    <option name="PROVIDER" value="MEMORY_ONLY" />
  </component>
</application>""")
    }
    return this
  }

  fun addBuildProcessProfiling(): IDETestContext {
    if (_resolvedProjectHome != null) {
      val ideaDir = resolvedProjectHome.resolve(".idea")
      val workspace = ideaDir.resolve("workspace.xml")

      if (workspace.toFile().exists()) {
        val newContent = StringBuilder()
        val readText = workspace.toFile().readText()
        val userLocalBuildProcessVmOptions = when {
          (testName.contains(
            "intellij_sources")) -> "-Dprofiling.mode=true -Dgroovyc.in.process=true -Dgroovyc.asm.resolving.only=false"
          else -> "-Dprofiling.mode=true"
        }
        if (readText.contains("CompilerWorkspaceConfiguration")) {
          workspace.toFile().readLines().forEach {
            if (it.contains("<component name=\"CompilerWorkspaceConfiguration\">")) {
              val newLine = "<component name=\"CompilerWorkspaceConfiguration\">\n<option name=\"COMPILER_PROCESS_ADDITIONAL_VM_OPTIONS\" value=\"$userLocalBuildProcessVmOptions\" />"
              newContent.appendLine(newLine)
            }
            else {
              newContent.appendLine(it)
            }
          }
          workspace.writeText(newContent.toString())
        }
        else {
          val xmlDoc = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().parse(workspace.toFile())

          xmlDoc.documentElement.normalize()

          val firstElement = xmlDoc.firstChild
          val componentElement = xmlDoc.createElement("component")
          componentElement.setAttribute("name", "CompilerWorkspaceConfiguration")
          val optionElement = xmlDoc.createElement("option")
          optionElement.setAttribute("name", "COMPILER_PROCESS_ADDITIONAL_VM_OPTIONS")
          optionElement.setAttribute("value", userLocalBuildProcessVmOptions)
          firstElement.appendChild(componentElement).appendChild(optionElement)
          val source = DOMSource(xmlDoc)
          val outputStream = FileOutputStream(workspace.toFile())
          val result = StreamResult(outputStream)
          val transformerFactory = TransformerFactory.newInstance()
          val transformer = transformerFactory.newTransformer()
          transformer.transform(source, result)
        }
      }
    }
    return this
  }

  fun checkThatBuildRunByIdea(): IDETestContext {
    if (_resolvedProjectHome != null) {
      val ideaDir = resolvedProjectHome.resolve(".idea")
      val gradle = ideaDir.resolve("gradle.xml")
      if (gradle.toFile().exists()) {
        val readText = gradle.toFile().readText()
        if (!readText.contains("<option name=\"delegatedBuild\" value=\"false\"/>")) {
          val xmlDoc = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().parse(gradle.toFile())

          xmlDoc.documentElement.normalize()

          val gradleProjectSettingsElements: NodeList = xmlDoc.getElementsByTagName("GradleProjectSettings")
          if (gradleProjectSettingsElements.length == 1) {

            for (i in 0 until gradleProjectSettingsElements.length) {
              val component: Node = gradleProjectSettingsElements.item(i)

              if (component.nodeType == Node.ELEMENT_NODE) {
                val optionElement = xmlDoc.createElement("option")
                optionElement.setAttribute("name", "delegatedBuild")
                optionElement.setAttribute("value", "false")
                component.appendChild(optionElement)
              }
            }
            val source = DOMSource(xmlDoc)
            val outputStream = FileOutputStream(gradle.toFile())
            val result = StreamResult(outputStream)
            val transformerFactory = TransformerFactory.newInstance()
            val transformer = transformerFactory.newTransformer()
            transformer.transform(source, result)
          }
        }
      }
    }
    return this
  }

  fun setBuildProcessHeapSize(heapSizeValue: String): IDETestContext {
    if (_resolvedProjectHome != null) {
      val heapSize = when (heapSizeValue.isEmpty()) {
        true -> "2000"
        else -> heapSizeValue
      }
      val ideaDir = resolvedProjectHome.resolve(".idea")
      val compilerXml = ideaDir.resolve("compiler.xml")
      if (compilerXml.toFile().exists()) {
        val newContent = StringBuilder()
        val readText = compilerXml.toFile().readText()
        if (!readText.contains("BUILD_PROCESS_HEAP_SIZE")) {
          compilerXml.toFile().readLines().forEach {
            if (it.contains("<component name=\"CompilerConfiguration\">")) {
              val newLine = "<component name=\"CompilerConfiguration\">\n<option name=\"BUILD_PROCESS_HEAP_SIZE\" value=\"$heapSize\" />"
              newContent.appendLine(newLine)
            }
            else {
              newContent.appendLine(it)
            }
          }
          compilerXml.writeText(newContent.toString())
        }
        else if (heapSizeValue.isNotEmpty()) {
          compilerXml.toFile().readLines().forEach {
            if (it.contains("BUILD_PROCESS_HEAP_SIZE")) {
              val newLine = it.replace("value=\"\\d*\"".toRegex(), "value=\"$heapSize\"")
              newContent.appendLine(newLine)
            }
            else {
              newContent.appendLine(it)
            }
          }
          compilerXml.writeText(newContent.toString())
        }
      }
    }
    return this
  }

  fun updateGeneralSettings(): IDETestContext {
    val patchedIdeGeneralXml = this::class.java.classLoader.getResourceAsStream("ide.general.xml")
    val pathToGeneralXml = paths.configDir.toAbsolutePath().resolve("options/ide.general.xml")

    if (!pathToGeneralXml.exists()) {
      pathToGeneralXml.parent.createDirectories()
      patchedIdeGeneralXml.use {
        if (it != null) {
          pathToGeneralXml.writeBytes(it.readAllBytes())
        }
      }
    }
    return this
  }

  @Suppress("unused")
  fun setLicense(pathToFileWithLicense: Path): IDETestContext {
    val licenseKeyFileName: String = when (this.ide.productCode) {
      IdeProductProvider.IU.productCode -> "idea.key"
      IdeProductProvider.RM.productCode -> "rubymine.key"
      IdeProductProvider.WS.productCode -> "webstorm.key"
      IdeProductProvider.PS.productCode -> "phpstorm.key"
      IdeProductProvider.GO.productCode -> "goland.key"
      IdeProductProvider.PY.productCode -> "pycharm.key"
      IdeProductProvider.DB.productCode -> "datagrip.key"
      else -> error("Setting license to the product ${this.ide.productCode} is not supported")
    }

    val keyFile = paths.configDir.resolve(licenseKeyFileName)
    keyFile.toFile().createNewFile()
    keyFile.toFile().writeText(pathToFileWithLicense.toFile().readText())
    return this
  }

  fun publishArtifact(source: Path,
                      artifactPath: String = testName,
                      artifactName: String = source.fileName.toString()) = ciServer.publishArtifact(source, artifactPath, artifactName)

  @Suppress("unused")
  fun withReportPublishing(isEnabled: Boolean): IDETestContext {
    isReportPublishingEnabled = isEnabled
    return this
  }
}

