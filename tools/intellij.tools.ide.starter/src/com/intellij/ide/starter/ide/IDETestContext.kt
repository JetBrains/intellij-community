package com.intellij.ide.starter.ide

import com.intellij.ide.starter.buildTool.BuildTool
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.frameworks.Framework
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.models.VMOptions.Companion.ALLOW_SKIPPING_FULL_SCANNING_ON_STARTUP_OPTION
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.profiler.ProfilerType
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.report.publisher.ReportPublisher
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.openTestCaseProject
import com.intellij.ide.starter.runner.startIdeWithoutProject
import com.intellij.ide.starter.telemetry.TestTelemetryService
import com.intellij.ide.starter.telemetry.computeWithSpan
import com.intellij.ide.starter.utils.FileSystem.deleteRecursivelyQuietly
import com.intellij.ide.starter.utils.XmlBuilder
import com.intellij.ide.starter.utils.replaceSpecialCharactersWithHyphens
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.findOrCreateFile
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import com.intellij.tools.ide.performanceTesting.commands.SdkObject
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.ui.NewUiValue
import com.intellij.util.io.createParentDirectories
import com.intellij.util.io.write
import kotlinx.coroutines.runBlocking
import org.kodein.di.direct
import org.kodein.di.factory
import org.kodein.di.instance
import org.kodein.di.newInstance
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import kotlin.io.path.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

open class IDETestContext(
  val paths: IDEDataPaths,
  val ide: InstalledIde,
  val testCase: TestCase<*>,
  val testName: String,
  @Suppress("PropertyName") val _resolvedProjectHome: Path?,
  var profilerType: ProfilerType = ProfilerType.NONE,
  val publishers: List<ReportPublisher> = di.direct.instance(),
  var isReportPublishingEnabled: Boolean = true,
  var preserveSystemDir: Boolean = false,
) {
  companion object {
    const val OPENTELEMETRY_FILE: String = "opentelemetry.json"

    private val SEARCH_EVERYWHERE_REGISTRY_KEYS: List<String> get() = listOf(
      "search.everywhere.new.enabled",
      "search.everywhere.new.rider.enabled",
      "search.everywhere.new.cwm.client.enabled"
    )
  }

  fun copy(ide: InstalledIde? = null, resolvedProjectHome: Path? = null): IDETestContext {
    return IDETestContext(paths, ide ?: this.ide, testCase, testName, resolvedProjectHome ?: this._resolvedProjectHome, profilerType,
                          publishers, isReportPublishingEnabled, preserveSystemDir)
  }

  val resolvedProjectHome: Path
    get() = checkNotNull(_resolvedProjectHome) { "Project directory is not specified for the test '$testName' in ${IDETestContext::class.java.name}" }

  val pluginConfigurator: PluginConfigurator by di.newInstance { factory<IDETestContext, PluginConfigurator>().invoke(this@IDETestContext) }

  inline fun <reified T, reified M : T> getInstanceFromBindSet(): M {
    val bindings: Set<T> by di.instance(arg = this@IDETestContext)
    return bindings.filterIsInstance<M>().single()
  }

  inline fun <reified M : BuildTool> withBuildTool(): M = getInstanceFromBindSet<BuildTool, M>()

  inline fun <reified M : Framework> withFramework(): M = getInstanceFromBindSet<Framework, M>()

  /**
   * Method applies patch immediately to the whole context.
   * If you want to apply VMOptions just for a single run, use [IDERunContext.addVMOptionsPatch].
   */
  open fun applyVMOptionsPatch(patchVMOptions: VMOptions.() -> Unit): IDETestContext {
    ide.vmOptions.patchVMOptions()
    return this
  }

  fun disableLinuxNativeMenuForce(): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("linux.native.menu.force.disable", true)
    }

  fun setMemorySize(sizeMb: Int): IDETestContext =
    applyVMOptionsPatch {
      withXmx(sizeMb)
    }

  fun setActiveProcessorCount(count: Int): IDETestContext =
    applyVMOptionsPatch {
      withActiveProcessorCount(count)
    }

  fun skipGitLogIndexing(value: Boolean = true): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("vcs.log.index.enable", !value)
    }

  fun executeRightAfterIdeOpened(executeRightAfterIdeOpened: Boolean = true): IDETestContext = applyVMOptionsPatch {
    executeRightAfterIdeOpened(executeRightAfterIdeOpened)
  }

  fun executeDuringIndexing(executeDuringIndexing: Boolean = true): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("performance.execute.script.after.scanning", executeDuringIndexing)
    }

  fun withGtk2OnLinux(): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("jdk.gtk.verbose", true)
      if (SystemInfo.isLinux) {
        addSystemProperty("jdk.gtk.version", 2)
      }
    }

  fun disableInstantIdeShutdown(): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("ide.instant.shutdown", false)
    }

  fun disableTraceDataSharingNotification(): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("ide.enable.notification.trace.data.sharing", false)
    }

  fun useOldUIInTests(): IDETestContext =
    applyVMOptionsPatch {
      @Suppress("DEPRECATION")
      removeSystemProperty(NewUiValue.KEY, true)
    }

  fun enableSlowOperationsInEdtInTests(): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("ide.slow.operations.assertion", false)
    }

  /**
   * Does not allow IDE to fork a process that sends FUS statistics on IDE shutdown.
   * On Windows that forked process may prevent some files from removing.
   * See [com.intellij.internal.statistic.EventLogApplicationLifecycleListener]
   */
  @Suppress("KDocUnresolvedReference")
  fun disableFusSendingOnIdeClose(): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("feature.usage.event.log.send.on.ide.close", false)
    }

  fun suppressStatisticsReport(): IDETestContext = applyVMOptionsPatch {
    addSystemProperty("idea.suppress.statistics.report", true)
  }

  fun disableReportingStatisticsToProduction(disabled: Boolean = true): IDETestContext = applyVMOptionsPatch {
    addSystemProperty("idea.local.statistics.without.report", disabled)
  }

  fun disableReportingStatisticToJetStat(): IDETestContext = applyVMOptionsPatch {
    addSystemProperty("idea.updates.url", "http://127.0.0.1")
  }

  fun withVerboseIndexingDiagnostics(dumpPaths: Boolean = false): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("intellij.indexes.diagnostics.limit.of.files", 10000)
      addSystemProperty("intellij.indexes.diagnostics.should.dump.paths.of.indexed.files", dumpPaths)
      // Dumping of lists of indexed file paths may require a lot of memory.
      withXmx(4 * 1024)
    }

  fun allowSkippingFullScanning(allow: Boolean): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty(ALLOW_SKIPPING_FULL_SCANNING_ON_STARTUP_OPTION, allow)
    }


  @Suppress("unused")
  fun collectMemorySnapshotOnFailedPluginUnload(): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("ide.plugins.snapshot.on.unload.fail", true)
    }

  @Suppress("unused")
  fun setPerProjectDynamicPluginsFlag(): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("ide.plugins.per.project", true)
    }

  fun disableAutoImport(disabled: Boolean = true): IDETestContext = applyVMOptionsPatch {
    disableAutoImport(disabled)
  }

  fun disableLoadShellEnv(disabled: Boolean = true): IDETestContext = applyVMOptionsPatch {
    disableLoadShellEnv(disabled)
  }

  fun setJdkDownloaderHome(path: Path): IDETestContext = applyVMOptionsPatch {
    addSystemProperty("jdk.downloader.home", path)
  }

  fun disableOrdinaryIndexes(): IDETestContext = applyVMOptionsPatch {
    addSystemProperty("idea.use.only.index.infrastructure.extension", true)
  }

  fun setSharedIndexesDownload(enable: Boolean = true): IDETestContext = applyVMOptionsPatch {
    addSystemProperty("shared.indexes.bundled", enable)
    addSystemProperty("shared.indexes.download", enable)
    addSystemProperty("shared.indexes.download.auto.consent", enable)
  }

  fun skipIndicesInitialization(value: Boolean = true): IDETestContext = applyVMOptionsPatch {
    skipIndicesInitialization(value)
  }

  fun doNotDisablePaidPluginsOnStartup(): IDETestContext = applyVMOptionsPatch {
    doNotDisablePaidPluginsOnStartup()
  }

  fun enableAsyncProfiler(): IDETestContext = applyVMOptionsPatch {
    addSystemProperty("integrationTests.profiler", "async")
  }

  fun enableYourKitProfiler(): IDETestContext = applyVMOptionsPatch {
    addSystemProperty("integrationTests.profiler", "yourkit")
  }

  fun enableWorkspaceModelVerboseLogs(): IDETestContext = applyVMOptionsPatch {
    configureLoggers(LogLevel.TRACE, "com.intellij.workspaceModel")
    configureLoggers(LogLevel.TRACE, "com.intellij.platform.workspace")
  }

  fun enableEventBusDebugLogs(): IDETestContext = applyVMOptionsPatch {
    addSystemProperty("eventbus.debug", true)
  }

  fun enableExternalSystemVerboseLogs(): IDETestContext = applyVMOptionsPatch {
    configureLoggers(LogLevel.TRACE, "com.intellij.openapi.externalSystem")
  }

  fun wipeSystemDir(): IDETestContext = apply {
    if (!preserveSystemDir) {
      //TODO: it would be better to allocate a new context instead of wiping the folder
      logOutput("Cleaning system dir for $this at $paths")
      paths.systemDir.deleteRecursivelyQuietly()
    }
    else {
      logOutput("Cleaning system dir for $this at $paths is disabled due to preserveSystemDir")
    }
  }

  fun wipeProjectsDir(): IDETestContext = apply {
    val path = paths.systemDir / "projects"
    logOutput("Cleaning project cache dir for $this at $path")
    path.deleteRecursivelyQuietly()
  }

  fun wipeEventLogDataDir(): IDETestContext = apply {
    val path = paths.systemDir / "event-log-data"
    logOutput("Cleaning event-log-data dir for $this at $path")
    path.deleteRecursivelyQuietly()
  }

  open fun wipeWorkspaceState(): IDETestContext = apply {
    val path = paths.configDir.resolve("workspace")
    logOutput("Cleaning workspace dir in config dir for $this at $path")
    path.deleteRecursivelyQuietly()
  }

  /**
   * Setup profiler injection on IDE start.
   * Make sure that you don't use start/stopProfiler in this case since this will cause: "Could not set dlopen hook. Unsupported JVM?"
   * exception. You have to choose between profiling from the start or profiling a specific part of the test.
   */
  open fun setProfiler(profilerType: ProfilerType = ProfilerType.ASYNC_ON_START): IDETestContext {
    logOutput("Setting profiler: ${profilerType}")
    this.profilerType = profilerType
    return this
  }

  fun internalMode(value: Boolean = true): IDETestContext = applyVMOptionsPatch { addSystemProperty("idea.is.internal", value) }

  /**
   * Cleans .idea and removes all the .iml files for project
   */
  fun prepareProjectCleanImport(): IDETestContext {
    return removeIdeaProjectDirectory().removeAllImlFilesInProject()
  }

  fun disableAutoSetupJavaProject(): IDETestContext = applyVMOptionsPatch {
    addSystemProperty("idea.java.project.setup.disabled", true)
  }

  fun disablePackageSearchBuildFiles(): IDETestContext = applyVMOptionsPatch {
    addSystemProperty("idea.pkgs.disableLoading", true)
  }

  fun disableAIAssistantToolwindowActivationOnStart(): IDETestContext = applyVMOptionsPatch {
    addSystemProperty("llm.ai.assistant.toolwindow.activation.on.start", false)
    addSystemProperty("llm.show.ai.promotion.window.on.start", false)
  }

  @Suppress("TestOnlyProblems")
  fun disableSplitSearchEverywhere(): IDETestContext = applyVMOptionsPatch {
    SEARCH_EVERYWHERE_REGISTRY_KEYS.forEach { addSystemProperty(it, false) }
  }

  @Suppress("TestOnlyProblems")
  fun enableSplitSearchEverywhere(): IDETestContext = applyVMOptionsPatch {
    SEARCH_EVERYWHERE_REGISTRY_KEYS.forEach { addSystemProperty(it, true) }
  }

  fun withKotlinPluginK2(): IDETestContext = applyVMOptionsPatch {
    addSystemProperty("idea.kotlin.plugin.use.k1", false)
  }

  fun enableCloudRegistry(registryHost: String): IDETestContext = applyVMOptionsPatch {
    addSystemProperty("ide.registry.refresh.debug", true)
    addSystemProperty("ide.registry.refresh.initial.delay.seconds", 0)
    addSystemProperty("ide.registry.refresh.host", registryHost)
  }

  fun enableHighlightingLog(logLevel: LogLevel = LogLevel.DEBUG): IDETestContext = applyVMOptionsPatch { configureLoggers(logLevel, "com.intellij.codeInsight") }

  fun enableCheckTrafficLight(enable: Boolean = true): IDETestContext = applyVMOptionsPatch { addSystemProperty("is.test.traffic.light", enable) }

  fun disableCLionPromoOnStart(): IDETestContext = applyVMOptionsPatch {
    addSystemProperty("clion.promo.on.start.enable", false)
  }

  fun removeIdeaProjectDirectory(): IDETestContext {
    val ideaDirPath = resolvedProjectHome.resolve(".idea")

    logOutput("Removing $ideaDirPath ...")

    if (ideaDirPath.notExists()) {
      logOutput("Idea project directory $ideaDirPath doesn't exist. So, it will not be deleted")
      return this
    }

    ideaDirPath.deleteRecursivelyQuietly()
    return this
  }

  fun wipeWorkspaceXml(): IDETestContext = apply {
    val workspaceXml = resolvedProjectHome / ".idea" / "workspace.xml"

    logOutput("Removing $workspaceXml ...")

    if (workspaceXml.notExists()) {
      logOutput("Workspace file $workspaceXml doesn't exist. So, it will not be deleted")
      return this
    }

    workspaceXml.deleteIfExists()
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

  fun determineDefaultCommandLineArguments(): (IDERunContext) -> IDECommandLine =
    if (this.testCase.projectInfo == NoProject) ::startIdeWithoutProject
    else ::openTestCaseProject

  fun runIDE(
    commandLine: (IDERunContext) -> IDECommandLine = determineDefaultCommandLineArguments(),
    commands: Iterable<MarshallableCommand> = CommandChain(),
    runTimeout: Duration = 10.minutes,
    useStartupScript: Boolean = true,
    launchName: String = "",
    expectedKill: Boolean = false,
    expectedExitCode: Int = 0,
    collectNativeThreads: Boolean = false,
    stdOut: ExecOutputRedirect? = null,
    configure: IDERunContext.() -> Unit = {},
  ) =
    runBlocking {
      runIdeSuspending(commandLine, commands, runTimeout, useStartupScript, launchName, expectedKill, expectedExitCode, collectNativeThreads, stdOut, configure)
    }

  /**
   * Entry point to run IDE.
   * If you want to run IDE without any project on start use [com.intellij.ide.starter.runner.IDECommandLine.StartIdeWithoutProject]
   */
  suspend fun runIdeSuspending(
    commandLine: (IDERunContext) -> IDECommandLine = determineDefaultCommandLineArguments(),
    commands: Iterable<MarshallableCommand> = CommandChain(),
    runTimeout: Duration = 10.minutes,
    useStartupScript: Boolean = true,
    launchName: String = "",
    expectedKill: Boolean = false,
    expectedExitCode: Int = 0,
    collectNativeThreads: Boolean = false,
    stdOut: ExecOutputRedirect? = null,
    configure: suspend IDERunContext.() -> Unit = {},
  ): IDEStartResult {
    val span = TestTelemetryService.spanBuilder("runIDE").setAttribute("launchName", launchName).startSpan()
    span.makeCurrent().use {
      val runContext = IDERunContext(
        testContext = this,
        commandLine = commandLine,
        commands = commands,
        runTimeout = runTimeout,
        useStartupScript = useStartupScript,
        launchName = launchName,
        expectedKill = expectedKill,
        expectedExitCode = expectedExitCode,
        collectNativeThreads = collectNativeThreads,
        stdOut = stdOut
      )
      configure(runContext)

      try {
        val ideRunResult = runContext.runIdeSuspending()
        if (isReportPublishingEnabled) {
          computeWithSpan("publisher") {
            for (it in publishers) {
              it.publishResultOnSuccess(ideRunResult)
            }
          }
        }
        if (ideRunResult.failureError != null) {
          throw ideRunResult.failureError
        }
        return ideRunResult
      }
      catch (throwable: Throwable) {
        if (isReportPublishingEnabled) publishers.forEach {
          it.publishResultOnException(runContext, throwable)
        }
        throw throwable
      }
      finally {
        if (isReportPublishingEnabled) publishers.forEach {
          it.publishAnywayAfterRun(runContext)
        }
        span.end()
      }
    }
  }

  fun removeAndUnpackProject(): IDETestContext {
    testCase.projectInfo.downloadAndUnpackProject()
    return this
  }

  fun setProviderMemoryOnlyOnLinux(): IDETestContext {
    if (!SystemInfo.isLinux) return this
    writeConfigFile("options/security.xml", """
      <application>
        <component name="PasswordSafe">
          <option name="PROVIDER" value="MEMORY_ONLY" />
        </component>
      </application>
    """)
    return this
  }

  fun updateGeneralSettings(configDir: Path = paths.configDir): IDETestContext {
    val patchedIdeGeneralXml = this::class.java.classLoader.getResourceAsStream("ide.general.xml")
    val pathToGeneralXml = configDir.toAbsolutePath().resolve("options/ide.general.xml")

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

  fun disableMinimap(): IDETestContext {
    writeConfigFile("options/Minimap.xml", """
      <application>
        <component name="Minimap">
          <option name="enabled" value="false" />
        </component>
      </application>
    """)
    return this
  }

  @Suppress("unused")
  fun setLicense(pathToFileWithLicense: Path): IDETestContext {
    val supportedProducts = listOf(IdeProductProvider.IU.productCode, IdeProductProvider.RM.productCode, IdeProductProvider.WS.productCode,
                                   IdeProductProvider.PS.productCode, IdeProductProvider.PS.productCode, IdeProductProvider.PS.productCode,
                                   IdeProductProvider.GO.productCode, IdeProductProvider.PY.productCode, IdeProductProvider.DB.productCode,
                                   IdeProductProvider.CL.productCode)
    if (this.ide.productCode !in supportedProducts) {
      error("Setting license to the product ${this.ide.productCode} is not supported")
    }
    return setLicense(String(Base64.getEncoder().encode(pathToFileWithLicense.readBytes())))
  }

  fun disableAutoCompletion(): IDETestContext {
    writeConfigFile("options/editor.xml", """
      <application>
        <component name="CodeInsightSettings">
          <option name="AUTO_POPUP_COMPLETION_LOOKUP" value="false" />
        </component>
      </application>
    """)
    return this
  }

  fun disableInsertingPairBrackets(): IDETestContext {
    writeConfigFile("options/editor.xml", """
      <application>
        <component name="CodeInsightSettings">
          <option name="AUTOINSERT_PAIR_BRACKET" value="false" />
          <option name="AUTOINSERT_PAIR_QUOTE" value="false" />
          <option name="INSERT_BRACE_ON_ENTER" value="false" />
        </component>
      </application>
    """)
    return this
  }

  /**
   * To get a license you need:
   * 1. Go to [JetBrains Account](https://account.jetbrains.com/licenses)
   * 2. "Download a code for offline activation"
   * 3. Activate license in IDE (Help|Register)
   * 4. `base64 -i <config_folder>/<ide>.key`
   * 5. Provide the resulting string to the method (via ENV variable, for example)
   */
  fun setLicense(license: String?): IDETestContext {
    if (license == null) {
      logOutput("License is not provided")
      return this
    }
    this.onRemDevContext {
      return frontendIDEContext.setLicense(license)
    }

    val licenseKeyFileName: String = when (this.ide.productCode) {
      IdeProductProvider.IU.productCode -> "idea.key"
      IdeProductProvider.RM.productCode -> "rubymine.key"
      IdeProductProvider.WS.productCode -> "webstorm.key"
      IdeProductProvider.PS.productCode -> "phpstorm.key"
      IdeProductProvider.GO.productCode -> "goland.key"
      IdeProductProvider.PY.productCode -> "pycharm.key"
      IdeProductProvider.DB.productCode -> "datagrip.key"
      IdeProductProvider.CL.productCode -> "clion.key"
      IdeProductProvider.QA.productCode -> "aqua.key"
      IdeProductProvider.RR.productCode -> "rustrover.key"
      IdeProductProvider.RD.productCode -> "rider.key"
      else -> return this
    }
    val keyFile = paths.configDir.resolve(licenseKeyFileName).toFile()
    keyFile.createNewFile()
    keyFile.writeBytes(Base64.getDecoder().decode(license))
    logOutput("License is set")
    return this
  }

  fun disableMigrationNotification(): IDETestContext {
    createMigrateConfig("properties intellij.first.ide.session")
    return this
  }

  @Suppress("unused")
  fun createMigrateConfigWithMergeConfigsProperty(): IDETestContext {
    createMigrateConfig("merge-configs")
    return this
  }

  @Suppress("unused")
  fun createMigrateConfigWithImportSettingsPath(path: Path): IDETestContext {
    createMigrateConfig("import $path")
    return this
  }

  fun createMigrateConfig(content: String = ""): IDETestContext {
    paths.configDir.resolve("migrate.config").findOrCreateFile().write(content)
    return this
  }

  fun publishArtifact(
    source: Path,
    artifactPath: String = testName,
    artifactName: String = source.fileName.toString(),
  ) {
    computeWithSpan("publish artifacts") { span ->
      span.setAttribute("artifactPath", artifactPath)
      span.setAttribute("artifactName", artifactName)
      CIServer.instance.publishArtifact(source, artifactPath.replaceSpecialCharactersWithHyphens(), artifactName.replaceSpecialCharactersWithHyphens())
    }
  }

  @Suppress("unused")
  fun withReportPublishing(isEnabled: Boolean): IDETestContext {
    isReportPublishingEnabled = isEnabled
    return this
  }

  fun addProjectToTrustedLocations(projectPath: Path? = null, addParentDir: Boolean = false, configPath: Path = paths.configDir): IDETestContext {
    if (this.testCase.projectInfo == NoProject && projectPath == null) return this

    val isRDProduct = this.ide.productCode == IdeProductProvider.RD.productCode

    val (path, expression) = when (addParentDir) {
      true -> Pair(first = projectPath?.parent ?: this.resolvedProjectHome.normalize().parent, second = when (isRDProduct) {
        true -> error("NOT_IMPLEMENTED please add a correct path")
        else -> "//component[@name='Trusted.Paths.Settings']/option[@name='TRUSTED_PATHS']/list"
      })
      else -> Pair(first = projectPath ?: this.resolvedProjectHome.normalize(), second = when (isRDProduct) {
        true -> "//component[@name='TrustedSolutionStore']/option[@name='trustedLocations']/set"
        else -> "//component[@name='Trusted.Paths']/option[@name='TRUSTED_PROJECT_PATHS']/map"
      })
    }

    val (trustedXmlPath, fileName) = when (isRDProduct) {
      true -> configPath.toAbsolutePath().resolve("options/trustedSolutions.xml") to "trustedSolutions.xml"
      else -> configPath.toAbsolutePath().resolve("options/trusted-paths.xml") to "trusted-paths.xml"
    }

    if (!trustedXmlPath.exists()) {
      trustedXmlPath.parent.createDirectories()
      Files.write(trustedXmlPath, this::class.java.classLoader.getResource(fileName)!!.readText().toByteArray())
    }
    else {
      if (trustedXmlPath.readText().contains("\"$path\"")) {
        logOutput("Trusted xml file content: ${trustedXmlPath.readText()}")
        return this
      }
    }

    try {
      val xmlDoc = XmlBuilder.parse(trustedXmlPath)
      val xp: XPath = XPathFactory.newInstance().newXPath()

      val component = xp.evaluate(expression, xmlDoc, XPathConstants.NODE) as Element
      val entry = when (addParentDir || isRDProduct) {
        true -> xmlDoc.createElement("option").apply { setAttribute("value", "${path}") }
        else -> xmlDoc.createElement("entry").apply {
          setAttribute("key", "$path")
          setAttribute("value", "true")
        }
      }
      component.appendChild(entry)

      XmlBuilder.writeDocument(xmlDoc, trustedXmlPath)
      logOutput("Trusted xml file content: ${trustedXmlPath.readText()}")
    }
    catch (e: Exception) {
      logError(e)
    }
    return this
  }


  @Suppress("unused")
  fun copyExistingConfig(configPath: Path): IDETestContext {
    @OptIn(ExperimentalPathApi::class)
    configPath.copyToRecursively(paths.configDir, followLinks = false, overwrite = true)
    return this
  }

  @Suppress("unused")
  fun copyExistingPlugins(pluginPath: Path): IDETestContext {
    @OptIn(ExperimentalPathApi::class)
    pluginPath.copyToRecursively(paths.pluginsDir, followLinks = false, overwrite = true)
    return this
  }

  fun setupSdk(sdkObjects: SdkObject?, cleanDirs: Boolean = true): IDETestContext = computeWithSpan("setupSdk") {
    if (sdkObjects == null) return this
    try {
      System.setProperty("DO_NOT_REPORT_ERRORS", "true")
      runIDE(
        commands = CommandChain()
          // TODO: hack to remove direct dependency on [intellij.tools.ide.performanceTesting.commands] module
          // It looks like actual shortcut from test code, so a proper solution for this should be implemented
          .addCommand("%setupSDK \"${sdkObjects.sdkName}\" \"${sdkObjects.sdkType}\" \"${sdkObjects.sdkPath}\"")
          .addCommand("%exitApp true"),
        launchName = "setupSdk",
        runTimeout = 3.minutes,
        configure = {
          addVMOptionsPatch {
            disableAutoImport(true)
            executeRightAfterIdeOpened(true)
            skipIndicesInitialization(true)
          }
        }
      )
    }
    finally {
      System.clearProperty("DO_NOT_REPORT_ERRORS")
    }
    if (cleanDirs)
      this
        //some caches from IDE warmup may stay
        .wipeSystemDir()
    return this
  }

  fun setKotestMaxCollectionEnumerateSize(): IDETestContext =
  // Need to generate the correct matcher when compared array is big.
    // kotest-assertions-core-jvm/5.5.4/kotest-assertions-core-jvm-5.5.4-sources.jar!/commonMain/io/kotest/matchers/collections/containExactly.kt:99
    applyVMOptionsPatch {
      addSystemProperty("kotest.assertions.collection.enumerate.size", Int.MAX_VALUE)
    }

  fun collectJBRDiagnosticFiles(javaProcessId: Long) {
    if (javaProcessId == 0L) return
    val userHome = System.getProperty("user.home")
    val pathUserHome = Paths.get(userHome)
    val javaErrorInIdeaFile = pathUserHome.resolve("java_error_in_idea_$javaProcessId.log")
    val jbrErrFile = pathUserHome.resolve("jbr_err_pid$javaProcessId.log")
    if (javaErrorInIdeaFile.exists()) {
      javaErrorInIdeaFile.copyTo(paths.jbrDiagnostic.resolve(javaErrorInIdeaFile.name).createParentDirectories())
    }
    if (jbrErrFile.exists()) {
      jbrErrFile.copyTo(paths.jbrDiagnostic.resolve(jbrErrFile.name).createParentDirectories())
    }
    if (paths.jbrDiagnostic.listDirectoryEntries().isNotEmpty()) {
      publishArtifact(paths.jbrDiagnostic)
    }
  }

  fun acceptNonTrustedCertificates(): IDETestContext {
    writeConfigFile("options/certificates.xml", """
      <application>
        <component name="CertificateManager">
          <option name="ACCEPT_AUTOMATICALLY" value="true" />
        </component>
      </application>
    """)
    return this
  }

  fun applyAppCdsIfNecessary(currentRepetition: Int): IDETestContext {
    // FIXME: IJPL-218141 enable app-cds back once it works with async profiler
    return this;
    //if (currentRepetition % 2 == 0) {
    //  // classes.jsa in jbr is not suitable for reuse, regenerate it, remove when it will be fixed
    //  val jbrDistroPath = if (OS.CURRENT == OS.macOS) ide.installationPath / "jbr" / "Contents" / "Home" else ide.installationPath / "jbr"
    //  if (jbrDistroPath.exists()) {
    //    JvmUtils.execJavaCmd(jbrDistroPath, listOf("-Xshare:dump"))
    //  }
    //  else {
    //    @Suppress("RAW_RUN_BLOCKING")
    //    JvmUtils.execJavaCmd(runBlocking(Dispatchers.Default) { ide.resolveAndDownloadTheSameJDK() }, listOf("-Xshare:dump"))
    //  }
    //  applyVMOptionsPatch {
    //    removeSystemClassLoader()
    //    addSharedArchiveFile(paths.systemDir / "ide.jsa")
    //  }
    //}
    //return this
  }

  fun disableStickyLines(): IDETestContext {
    writeConfigFile("options/editor.xml", """
      <application>
        <component name="EditorSettings">
          <option name="SHOW_STICKY_LINES" value="false" />
        </component>
      </application>
    """)
    return this
  }

  private fun writeConfigFile(relativePath: String, text: String): IDETestContext {
    val configFile = paths.configDir.toAbsolutePath().resolve(relativePath)
    configFile.parent.createDirectories()
    configFile.writeText(text.trimIndent())
    return this
  }

  fun enableDocRendering(): IDETestContext {
    writeConfigFile("options/editor.xml", """
      <application>
        <component name="EditorSettings">
          <option name="ENABLE_RENDERED_DOC" value="true" />
        </component>
      </application>
    """)
    return this
  }

  @Suppress("unused")
  fun enableProxyAutodetection(): IDETestContext {
    writeConfigFile("options/proxy.settings.xml", """
     <application>
       <component name="HttpConfigurable">
         <option name="USE_PROXY_PAC" value="true" />
       </component>
     </application> 
    """)
    return this
  }

  /**
   * Disables the "first startup" functionality, such as auto-trial, settings import, etc.
   */
  fun removeMigrateConfigAndCreateStubFile(): IDETestContext {
    paths.configDir.resolve("test.txt").createParentDirectories().createFile()
    paths.configDir.resolve("migrate.config").deleteIfExists()
    return this
  }

  /**
   * Configures a localhost proxy to disable internet access for the IDE
   */
  fun setLocalhostProxy(): IDETestContext {
    writeConfigFile("options/proxy.settings.xml", """
      <application>
        <component name="HttpConfigurable">
          <option name="USE_HTTP_PROXY" value="true" />
          <option name="PROXY_HOST" value="localhost" />
          <option name="PROXY_PORT" value="3128" />
          <option name="PROXY_EXCEPTIONS" value="" />
        </component>
      </application>
    """)
    return this
  }
}
