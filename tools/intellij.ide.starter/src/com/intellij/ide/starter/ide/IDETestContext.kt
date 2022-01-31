package com.intellij.ide.starter.ide

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ide.command.MarshallableCommand
import com.intellij.ide.starter.models.*
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.profiler.ProfilerType
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.logOutput
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.stream.IntStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.io.path.*
import kotlin.time.Duration

data class IDETestContext(
  val paths: IDEDataPaths,
  val ide: InstalledIDE,
  val test: StartUpPerformanceCase,
  val testName: String,
  private val _resolvedProjectHome: Path?,
  val patchVMOptions: VMOptions.() -> VMOptions,
  val ciServer: CIServer,
  var profilerType: ProfilerType = ProfilerType.NONE
) {
  companion object {
    const val TEST_RESULT_FILE_PATH_PROPERTY = "test.result.file.path"
  }

  val resolvedProjectHome: Path
    get() = checkNotNull(_resolvedProjectHome) { "Project is not found for the test $testName" }

  val localMavenRepo: Path
    get() = paths.tempDir.resolve(".m3").resolve("repository")

  val localGradleRepo: Path
    get() = paths.tempDir.resolve("gradle")

  fun addVMOptionsPatch(patchVMOptions: VMOptions.() -> VMOptions) = copy(
    patchVMOptions = this.patchVMOptions.andThen(patchVMOptions)
  )

  fun addLockFileForUITest(fileName: String): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("uiLockTempFile", paths.tempDir / fileName)
    }

  fun disableJcef(): IDETestContext =
    addVMOptionsPatch {
      // Disable JCEF (IDEA-243147). Otherwise tests will fail with LOG.error
      addSystemProperty("ide.browser.jcef.enabled", false)
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

  fun useNewMavenLocalRepository(): IDETestContext {
    localMavenRepo.toFile().mkdirs()
    return addVMOptionsPatch { addSystemProperty("idea.force.m2.home", localMavenRepo.toString()) }
  }

  fun disableGitLogIndexing(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("vcs.log.index.git", false)
    }

  fun useNewGradleLocalCache(): IDETestContext {
    localGradleRepo.toFile().mkdirs()
    return addVMOptionsPatch { addSystemProperty("gradle.user.home", localGradleRepo.toString()) }
  }

  fun executeAfterProjectOpening(executeAfterProjectOpening: Boolean = true) = this.addVMOptionsPatch {
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

  fun setPathForMemorySnapshot(): IDETestContext {
    return this.addVMOptionsPatch {
      this
        .addSystemProperty("memory.snapshots.path", paths.logsDir)
    }
  }

  fun collectMemorySnapshotOnFailedPluginUnload(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("ide.plugins.snapshot.on.unload.fail", true)
    }

  fun setPerProjectDynamicPluginsFlag(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("ide.plugins.per.project", true)
    }

  fun disableAutoImport(disabled: Boolean = true) = this.addVMOptionsPatch {
    this.addSystemProperty("external.system.auto.import.disabled", disabled)
  }

  fun disableOrdinaryIndexes() = this.addVMOptionsPatch {
    this.addSystemProperty("idea.use.only.index.infrastructure.extension", true)
  }

  fun setSharedIndexesDownload(enable: Boolean = true) = this.addVMOptionsPatch {
    addSystemProperty("shared.indexes.bundled", enable)
      .addSystemProperty("shared.indexes.download", enable)
      .addSystemProperty("shared.indexes.download.auto.consent", enable)
  }

  fun skipIndicesInitialization() = this.addVMOptionsPatch {
    this.addSystemProperty("idea.skip.indices.initialization", true)
  }

  fun addTestResultFilePath() = this.addVMOptionsPatch {
    this.addSystemProperty(TEST_RESULT_FILE_PATH_PROPERTY, paths.tempDir.resolve("testResult.txt"))
  }

  fun collectImportProjectPerfMetrics() = this.addVMOptionsPatch {
    this.addSystemProperty("idea.collect.project.import.performance", true)
  }

  fun enableWorkspaceModelVerboseLogs() = this.addVMOptionsPatch {
    this.addSystemProperty("idea.log.trace.categories", "#com.intellij.workspaceModel")
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

  fun wipeProjectsDir() = apply {
    val path = paths.systemDir / "projects"
    logOutput("Cleaning project cache dir for $this at $path")
    path.toFile().deleteRecursively()
  }

  fun wipeSnapshotDir() = apply {
    val path = paths.snapshotsDir
    logOutput("Cleaning snapshot dir for $this at $path")
    path.toFile().deleteRecursively()
  }

  // TODO: get rid of this method. It's confusing since we're already specifying, what profiler we want to use in [setProfiler]
  fun runContext(withProfiling: Boolean = true): IDERunContext {
    return IDERunContext(testContext = this).run {
      when (withProfiling) {
        true -> this.installProfiler()
        false -> this
      }
    }
  }

  /**
   * Setup profiler injection
   */
  @Suppress("unused")
  fun setProfiler(profilerType: ProfilerType): IDETestContext {
    this.profilerType = profilerType
    return this
  }

  fun internalMode() = this.addVMOptionsPatch { this.addSystemProperty("idea.is.internal", true) }

  fun prepareProjectCleanImport(): IDETestContext {
    return removeIdeaProjectDirectory().removeAllImlFilesInProject()
  }

  fun removeMavenConfigFiles(): IDETestContext {
    logOutput("Removing Maven config files in $resolvedProjectHome ...")

    resolvedProjectHome.toFile().walkTopDown()
      .forEach {
        if (it.isFile && it.name == "pom.xml") {
          it.delete()
          logOutput("File ${it.path} is deleted")
        }
      }

    return this
  }

  fun disableAutoSetupJavaProject() = addVMOptionsPatch {
    this.addSystemProperty("idea.java.project.setup.disabled", true)
  }

  fun disablePackageSearchBuildFiles() = addVMOptionsPatch {
    this.addSystemProperty("idea.pkgs.disableLoading", true)
  }

  fun removeGradleConfigFiles(): IDETestContext {
    logOutput("Removing Gradle config files in $resolvedProjectHome ...")

    resolvedProjectHome.toFile().walkTopDown()
      .forEach {
        if (it.isFile && (it.extension == "gradle" || (it.name in listOf("gradlew", "gradlew.bat", "gradle.properties")))) {
          it.delete()
          logOutput("File ${it.path} is deleted")
        }
      }

    return this
  }

  fun removeIdeaProjectDirectory(): IDETestContext {
    val ideaDirPath = this.resolvedProjectHome.resolve(".idea")

    logOutput("Removing $ideaDirPath ...")

    if (ideaDirPath.notExists()) {
      logOutput("Idea project directory $ideaDirPath doesn't exist. So, it will not be deleted")
      return this
    }

    ideaDirPath.toFile().deleteRecursively()
    return this
  }

  fun removeAllImlFilesInProject(): IDETestContext {
    val projectDir = this.resolvedProjectHome

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

  fun addPropertyToGradleProperties(property: String, value: String): IDETestContext {
    val projectDir = this.resolvedProjectHome
    val gradleProperties = projectDir.resolve("gradle.properties")
    val lineWithTheSameProperty = gradleProperties.readLines().singleOrNull { it.contains(property) }
    if (lineWithTheSameProperty != null) {
      if (lineWithTheSameProperty.contains(value)) {
        return this
      }
      val newValue = lineWithTheSameProperty.substringAfter("$property=") + " $value"
      val tempFile = File.createTempFile("newContent", ".txt").toPath()
      gradleProperties.forEachLine { line ->
        tempFile.appendText(when {
                              line.contains(property) -> "$property=$newValue" + System.getProperty("line.separator")
                              else -> line + System.getProperty("line.separator")
                            })
      }
      gradleProperties.writeText(tempFile.readText())
    }
    else {
      gradleProperties.appendLines(listOf("$property=$value"))
    }
    return this
  }

  fun runIDE(
    patchVMOptions: VMOptions.() -> VMOptions = { this },
    commandLine: IDECommandLine? = null,
    commands: Iterable<MarshallableCommand>,
    codeBuilder: (CodeInjector.() -> Unit)? = null,
    runTimeout: Duration = Duration.minutes(10),
    useStartupScript: Boolean = true,
    launchName: String = "",
    withProfiling: Boolean = true,
    expectedKill: Boolean = false
  ): IDEStartResult {
    return runContext(withProfiling)
      .copy(
        commandLine = commandLine,
        commands = commands,
        codeBuilder = codeBuilder,
        runTimeout = runTimeout,
        useStartupScript = useStartupScript,
        launchName = launchName,
        expectedKill = expectedKill
      )
      .addVMOptionsPatch(patchVMOptions)
      .runIDE()
  }

  fun warmUp(
    patchVMOptions: VMOptions.() -> VMOptions = { this },
    commands: Iterable<MarshallableCommand>,
    runTimeout: Duration = Duration.minutes(10)
  ): IDEStartResult {

    return runIDE(
      patchVMOptions = {
        val warmupReports = IDEStartupReports(paths.reportsDir / "warmUp")
        enableStartupPerformanceLog(warmupReports).enableClassLoadingReport(paths.logsDir / "class-report.txt").patchVMOptions()
      },
      commands = test.commands.plus(commands),
      runTimeout = runTimeout,
      launchName = "warmUp",
      withProfiling = false
    )
  }

  fun configureSpringCheck(): IDETestContext {
    val miscXml = resolvedProjectHome.resolve(".idea").resolve("misc.xml")
    if (!miscXml.exists()) {
      return this
    }

    val finalConfig = StringBuilder(miscXml.readText().length)
    miscXml.useLines { lines ->
      lines.forEach { line ->
        finalConfig.append(line.replace("  <component name=\"FrameworkDetectionExcludesConfiguration\">",
                                        "  <component name=\"FrameworkDetectionExcludesConfiguration\">\n" +
                                        "    <type id=\"Spring\" />"))
        finalConfig.append("\n")
      }
    }
    miscXml.writeText(finalConfig)
    return this
  }

  fun setProviderMemoryOnlyOnLinux(): IDETestContext {
    if (SystemInfo.isLinux) {
      val optionsConfig = this.paths.configDir.resolve("options")
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

  fun setGradleJvmInProject(useJavaHomeAsGradleJvm: Boolean = true): IDETestContext {
    if (this._resolvedProjectHome != null) {
      val ideaDir = this.resolvedProjectHome.resolve(".idea")
      val gradleXml = ideaDir.resolve("gradle.xml")

      if (gradleXml.toFile().exists()) {
        val xmlDoc = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().parse(gradleXml.toFile())
        xmlDoc.documentElement.normalize()

        val gradleSettings = xmlDoc.getElementsByTagName("GradleProjectSettings")
        if (gradleSettings.length == 1) {
          val options = (gradleSettings.item(0) as Element).getElementsByTagName("option")
          IntStream
            .range(0, options.length)
            .mapToObj { i -> options.item(i) as Element }
            .filter { it.getAttribute("name") == "gradleJvm" }
            .findAny()
            .ifPresent { node -> gradleSettings.item(0).removeChild(node) }

          if (useJavaHomeAsGradleJvm) {
            val option = xmlDoc.createElement("option")
            option.setAttribute("name", "gradleJvm")
            option.setAttribute("value", "#JAVA_HOME")
            gradleSettings.item(0).appendChild(option)
          }

          val source = DOMSource(xmlDoc)
          val outputStream = FileOutputStream(gradleXml.toFile())
          val result = StreamResult(outputStream)
          val transformerFactory = TransformerFactory.newInstance()
          val transformer = transformerFactory.newTransformer()
          transformer.transform(source, result)
          outputStream.close()
        }
      }
    }
    return this
  }

  fun addBuildProcessProfiling(): IDETestContext {
    if (this._resolvedProjectHome != null) {
      val ideaDir = this.resolvedProjectHome.resolve(".idea")
      val workspace = ideaDir.resolve("workspace.xml")

      if (workspace.toFile().exists()) {
        val newContent = StringBuilder()
        val readText = workspace.toFile().readText()
        val userLocalBuildProcessVmOptions = when {
          (this.testName.contains(
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
    if (this._resolvedProjectHome != null) {
      val ideaDir = this.resolvedProjectHome.resolve(".idea")
      val gradle = ideaDir.resolve("gradle.xml")
      if (gradle.toFile().exists()) {
        val readText = gradle.toFile().readText()
        if (!readText.contains("<option name=\"delegatedBuild\" value=\"false\"/>")) {
          val xmlDoc = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder().parse(gradle.toFile())

          xmlDoc.documentElement.normalize()

          val gradleProjectSettingsElements: NodeList = xmlDoc.getElementsByTagName("GradleProjectSettings")
          check(gradleProjectSettingsElements.length == 1)

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
    return this
  }

  fun updateBuildProcessHeapSize(): IDETestContext {
    if (this._resolvedProjectHome != null) {
      val ideaDir = this.resolvedProjectHome.resolve(".idea")
      val compilerXml = ideaDir.resolve("compiler.xml")
      if (compilerXml.toFile().exists()) {
        val newContent = StringBuilder()
        val readText = compilerXml.toFile().readText()
        if (!readText.contains("BUILD_PROCESS_HEAP_SIZE")) {
          compilerXml.toFile().readLines().forEach {
            if (it.contains("<component name=\"CompilerConfiguration\">")) {
              val newLine = "<component name=\"CompilerConfiguration\">\n<option name=\"BUILD_PROCESS_HEAP_SIZE\" value=\"2000\" />"
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
    val patchedIdeGeneralXml = File(this::class.java.classLoader.getResource("ide.general.xml")!!.path)
    val pathToGeneralXml = paths.configDir.toAbsolutePath().resolve("options/ide.general.xml")
    if (!pathToGeneralXml.exists()) {
      patchedIdeGeneralXml.copyTo(pathToGeneralXml.toFile())
    }
    return this
  }

  fun publishArtifact(source: Path,
                      artifactPath: String = testName,
                      artifactName: String = source.fileName.toString()) = ciServer.publishArtifact(source, artifactPath, artifactName)
}