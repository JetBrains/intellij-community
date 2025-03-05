// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.diagnostic.VMOptions
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.externalSystem.issue.BuildIssueException
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.platform.diagnostic.telemetry.OtlpConfiguration.getTraceEndpointURI
import com.intellij.platform.diagnostic.telemetry.impl.agent.AgentConfiguration.Companion.forService
import com.intellij.platform.diagnostic.telemetry.impl.agent.AgentConfiguration.Settings.Companion.withoutMetrics
import com.intellij.platform.diagnostic.telemetry.impl.agent.TelemetryAgentProvider.getJvmArgs
import com.intellij.platform.diagnostic.telemetry.impl.agent.TelemetryAgentResolver.getAgentLocation
import com.intellij.platform.diagnostic.telemetry.rt.context.TelemetryContext
import com.intellij.util.PathUtil
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.MavenVersionAwareSupportExtension
import org.jetbrains.idea.maven.MavenVersionSupportUtil
import org.jetbrains.idea.maven.buildtool.quickfix.InstallMaven2BuildIssue
import org.jetbrains.idea.maven.server.MavenServerCMDState.MemoryProperty.MemoryPropertyType
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.MavenUtil.getIdeaVersionToPassToMavenProcess
import org.jetbrains.idea.maven.utils.MavenUtil.isMavenUnitTestModeEnabled
import org.jetbrains.idea.maven.utils.MavenUtil.propertiesFromMavenOpts
import org.slf4j.Logger
import org.slf4j.jul.JDK14LoggerFactory
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.function.Supplier
import java.util.regex.Pattern

@ApiStatus.Internal
open class MavenServerCMDState(
  protected val myJdk: Sdk,
  protected val myVmOptions: String?,
  protected val myDistribution: MavenDistribution,
  protected val myDebugPort: Int?,
) : CommandLineState(null) {
  protected open fun createJavaParameters(): SimpleJavaParameters {
    if (!myDistribution.isValid()) {
      MavenLog.LOG.warn("Maven Distribution $myDistribution is not valid")
      throw IllegalArgumentException("Maven distribution at " + myDistribution.mavenHome.toAbsolutePath() + " is not valid")
    }

    val params = SimpleJavaParameters()

    params.jdk = myJdk

    params.workingDirectory = this.getWorkingDirectory()

    val defs = HashMap<String, String>(this.getMavenOpts())

    configureSslRelatedOptions(defs)

    defs.put("java.awt.headless", "true")
    for (each in defs.entries) {
      params.vmParametersList.defineProperty(each.key, each.value)
    }

    params.vmParametersList.addProperty("maven.defaultProjectBuilder.disableGlobalModelCache", "true")

    if (myDebugPort != null) {
      params.vmParametersList.addParametersString("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=*:$myDebugPort")
      params.programParametersList.add("runWithDebugger")
    }

    params.vmParametersList.addProperty("maven.defaultProjectBuilder.disableGlobalModelCache", "true")
    if (`is`("maven.collect.local.stat")) {
      params.vmParametersList.addProperty("maven.collect.local.stat", "true")
    }

    if (`is`("maven.delegate.trust.ssl.to.ide")) {
      params.vmParametersList.addProperty("delegate.ssl.to.ide", "true")
    }

    val profilerOption: String? = profilerVMString
    if (profilerOption != null) {
      params.vmParametersList
        .addParametersString(profilerOption)
    }

    var xmxProperty: String? = null
    var xmsProperty: String? = null

    if (myVmOptions != null) {
      val mavenOptsList = ParametersList()
      mavenOptsList.addParametersString(myVmOptions)

      for (param in mavenOptsList.parameters) {
        if (param.startsWith("-Xmx")) {
          xmxProperty = param
          continue
        }
        if (param.startsWith("-Xms")) {
          xmsProperty = param
          continue
        }
        if (`is`("maven.server.vm.remove.javaagent") && param.startsWith("-javaagent")) {
          continue
        }
        params.vmParametersList.add(param)
      }
    }
    params.vmParametersList.add("-Didea.version=" + getIdeaVersionToPassToMavenProcess())


    params.vmParametersList.addProperty(MavenServerEmbedder.MAVEN_EMBEDDER_VERSION, myDistribution.version)

    val extension = MavenVersionSupportUtil.getExtensionFor(myDistribution)
    checkExtension(extension)
    setupMainClass(params, extension!!)
    checkNotNull(extension) //checked in the method above, need to make static analyzer happy
    params.classPath.addAllFiles(
      extension.collectClassPathAndLibsFolder(myDistribution).map { it: Path? -> it!!.toFile() })

    val classPath = collectRTLibraries(myDistribution.version)
    for (s in classPath) {
      params.classPath.add(s)
    }

    val embedderXmx = System.getProperty("idea.maven.embedder.xmx")
    if (embedderXmx != null) {
      xmxProperty = "-Xmx$embedderXmx"
    }
    else {
      if (xmxProperty == null) {
        xmxProperty = getMaxXmxStringValue("-Xmx768m", xmsProperty)
      }
    }
    params.vmParametersList.add(xmsProperty)
    params.vmParametersList.add(xmxProperty)


    val mavenEmbedderParameters = System.getProperty("idea.maven.embedder.parameters")
    if (mavenEmbedderParameters != null) {
      params.programParametersList.addParametersString(mavenEmbedderParameters)
    }

    val mavenEmbedderCliOptions = System.getProperty(MavenServerEmbedder.MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS)
    if (mavenEmbedderCliOptions != null) {
      params.vmParametersList.addProperty(MavenServerEmbedder.MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS, mavenEmbedderCliOptions)
    }

    //workaround for JDK-4716483
    params.vmParametersList.addProperty("sun.rmi.server.exceptionTrace", "true")

    if (`is`("maven.server.opentelemetry.agent.enabled", false)) {
      attachTelemetryAgent(params)
    }

    setupMainExt(params)
    return params
  }

  private fun checkExtension(extension: MavenVersionAwareSupportExtension?) {
    if (extension == null) {
      if (StringUtil.compareVersionNumbers(myDistribution.version, "3") < 0) {
        throw BuildIssueException(InstallMaven2BuildIssue())
      }
      throw IllegalStateException("Maven distribution at" + myDistribution.mavenHome.toAbsolutePath() + " is not supported")
    }
    MavenLog.LOG.info("Using extension $extension to start MavenServer")
  }

  private fun setupMainExt(params: SimpleJavaParameters) {
    //it is critical to setup maven.ext.class.path for maven >=3.6, otherwise project extensions will not be loaded
    MavenUtil.addEventListener(myDistribution.version!!, params)
  }

  protected open fun getMavenOpts(): Map<String, String> = propertiesFromMavenOpts

  protected open fun getWorkingDirectory(): String = PathManager.getBinPath()

  protected fun collectRTLibraries(mavenVersion: String?): MutableCollection<String?> {
    val classPath: MutableSet<String?> = LinkedHashSet<String?>()
    if (StringUtil.compareVersionNumbers(mavenVersion, "3.1") < 0) {
      classPath.add(PathUtil.getJarPathForClass(Logger::class.java))
      classPath.add(PathUtil.getJarPathForClass(JDK14LoggerFactory::class.java))
    }

    classPath.add(PathUtil.getJarPathForClass(StringUtilRt::class.java)) //util-rt
    classPath.add(PathUtil.getJarPathForClass(NotNull::class.java)) //annotations-java5
    classPath.add(PathUtil.getJarPathForClass(Element::class.java)) //JDOM
    return classPath
  }

  private fun setupMainClass(params: SimpleJavaParameters, extension: MavenVersionAwareSupportExtension) {
    if (setupThrowMainClass && isMavenUnitTestModeEnabled()) {
      params.mainClass = MAIN_CLASS_WITH_EXCEPTION_FOR_TESTS
    }
    else {
      params.mainClass = extension.getMainClass(myDistribution)
    }
  }

  @Throws(ExecutionException::class)
  override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
    val processHandler = startProcess()
    return DefaultExecutionResult(processHandler)
  }

  @Throws(ExecutionException::class)
  override fun startProcess(): ProcessHandler {
    val params = createJavaParameters()
    val commandLine = params.toCommandLine()
    val processHandler: OSProcessHandler = OSProcessHandler.Silent(commandLine)
    processHandler.setShouldDestroyProcessRecursively(false)
    return processHandler
  }

  internal class MemoryProperty(val type: String, value: Long, unit: String?) {
    val valueBytes: Long
    val unit: MemoryUnit

    init {
      this.unit = if (unit != null) MemoryUnit.valueOf(unit.uppercase(Locale.getDefault())) else MemoryUnit.B
      this.valueBytes = value * this.unit.ratio
    }

    override fun toString(): String {
      return toString(unit)
    }

    fun toString(unit: MemoryUnit): String {
      return type + valueBytes / unit.ratio + unit.name.lowercase(Locale.getDefault())
    }

    internal enum class MemoryUnit(val ratio: Int) {
      B(1), K(B.ratio * 1024), M(K.ratio * 1024), G(M.ratio * 1024)
    }

    internal enum class MemoryPropertyType(val type: String) {
      XMX("-Xmx"),
    }

    companion object {
      private val MEMORY_PROPERTY_PATTERN: Pattern = Pattern.compile("^(-Xmx|-Xms)(\\d+)([kK]|[mM]|[gG])?$")
      fun of(propertyType: MemoryPropertyType, bytes: Long): MemoryProperty {
        return MemoryProperty(propertyType.type, bytes, MemoryUnit.B.name)
      }

      fun valueOf(value: String?): MemoryProperty? {
        if (value == null) return null
        val matcher = MEMORY_PROPERTY_PATTERN.matcher(value)
        if (matcher.find()) {
          return MemoryProperty(matcher.group(1), matcher.group(2).toLong(), matcher.group(3))
        }
        LOG.warn("$value not match $MEMORY_PROPERTY_PATTERN")
        return null
      }
    }
  }

  companion object {
    private val LOG = com.intellij.openapi.diagnostic
      .Logger.getInstance(MavenServerCMDState::class.java)
    private var setupThrowMainClass = false

    private const val MAIN_CLASS_WITH_EXCEPTION_FOR_TESTS: @NonNls String = "org.jetbrains.idea.maven.server.RemoteMavenServerThrowsExceptionForTests"


    private val profilerVMString: String?
      // Profile the Maven server if the idea is launched under profiling
      get() {
        val profilerOptionPrefix = "-agentpath:"
        val profilerVMOption = VMOptions.readOption(profilerOptionPrefix, true)
        val isIntegrationTest = System.getProperty("test.build_tool.daemon.profiler") != null
        // Doesn't work for macOS with java 11. Pending update to https://github.com/async-profiler/async-profiler/releases/tag/v3.0
        if (profilerVMOption == null || SystemInfo.isMac || !isIntegrationTest) return null
        val currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("mm:ss"))
        return profilerOptionPrefix + profilerVMOption
          .replace(".jfr", "-$currentTime-maven.jfr")
          .replace(".log", "-$currentTime-maven.log")
      }

    private fun configureSslRelatedOptions(defs: MutableMap<String, String>) {
      for (each in System.getProperties().entries) {
        val key = each.key
        val value = each.value
        if (key is String && value is String && key.startsWith("javax.net.ssl")) {
          defs.put(key, value)
        }
      }
    }

    private fun attachTelemetryAgent(params: SimpleJavaParameters) {
      val traceEndpoint = getTraceEndpointURI()
      if (traceEndpoint == null) {
        return
      }
      val agentLocation = getAgentLocation()
      if (agentLocation == null) {
        return
      }
      val configuration = forService(
        "MavenServer",
        TelemetryContext.current(),
        traceEndpoint,
        agentLocation,
        withoutMetrics()
      )
      val args = getJvmArgs(configuration)
      val parametersList = params.vmParametersList
      parametersList.addAll(args)
    }

    @TestOnly
    fun withThrowExceptionOnServerStart(runnable: Runnable) {
      setupThrowMainClass = true
      try {
        runnable.run()
      }
      finally {
        setupThrowMainClass = false
      }
    }


    @JvmStatic
    fun getMaxXmxStringValue(memoryValueA: String?, memoryValueB: String?): String? {
      val propertyA = MemoryProperty.Companion.valueOf(memoryValueA)
      val propertyB = MemoryProperty.Companion.valueOf(memoryValueB)
      if (propertyA != null && propertyB != null) {
        val maxMemoryProperty = if (propertyA.valueBytes > propertyB.valueBytes) propertyA else propertyB
        return MemoryProperty.Companion.of(MemoryPropertyType.XMX, maxMemoryProperty.valueBytes).toString(maxMemoryProperty.unit)
      }
      return Optional
        .ofNullable<MemoryProperty?>(propertyA).or(Supplier { Optional.ofNullable<MemoryProperty?>(propertyB) })
        .map<String?>(java.util.function.Function { property: MemoryProperty? ->
          MemoryProperty.Companion.of(MemoryPropertyType.XMX, property!!.valueBytes).toString(
            property.unit)
        })
        .orElse(null)
    }
  }
}
