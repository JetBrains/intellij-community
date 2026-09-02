package com.intellij.ide.starter.ci.teamcity

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.ignoredTestFailuresPattern
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.TestMetadata
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.TestOutcome
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.SyntheticTestKind
import com.intellij.platform.testFramework.teamCity.TeamCityReporter
import com.intellij.tools.ide.util.common.logOutput
import org.kodein.di.direct
import org.kodein.di.instance
import java.net.URI
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader

fun CIServer.asTeamCity(): TeamCityCIServer = this as TeamCityCIServer

open class TeamCityCIServer(
  private val systemPropertiesFilePath: Path? = try {
    Path(System.getenv("TEAMCITY_BUILD_PROPERTIES_FILE"))
  }
  catch (_: Exception) {
    null
  }
) : CIServer {
  private val codeOwnerResolver by lazy { di.direct.instance<CodeOwnerResolver>() }

  /**
   * Publishes [source] only on a TeamCity build. Off CI the artifact stays where the launch wrote it.
   *
   * The client copies the whole tree into `teamcity-artifacts-for-publish` and creates a new suffixed directory
   * on every call, and nothing deletes it. A local run publishes its logs, snapshots and reports after every
   * launch, so the copy grew without bound on a developer machine, while the originals were already on disk.
   */
  override fun publishArtifact(source: Path, artifactPath: String, artifactName: String) {
    if (!isBuildRunningOnCI) {
      logOutput("Not running on TeamCity, so '$artifactName' stays at $source and is not staged for publishing")
      return
    }
    TeamCityClient.publishTeamCityArtifacts(source = source, artifactPath = artifactPath, artifactName = artifactName)
  }

  fun addBisectMetadata(testName: String? = null, flowId: String? = null, testClassFqn: String? = CurrentTestMethod.get()?.clazz) {
    val meta = bisectMetadata(testClassFqn)
    TeamCityReporter.reportTestMetadata(testName = testName, type = meta.type, flowId = flowId, name = meta.name, value = meta.value)
  }

  private fun bisectMetadata(testClassFqn: String? = CurrentTestMethod.get()?.clazz): TestMetadata {
    val url = "https://ij-perf.labs.jb.gg/bisect/launcher?buildId=$buildId" +
              (testClassFqn?.let { "&testPatterns=$it" } ?: "")
    return TestMetadata(name = "Start bisect", value = url, type = TeamCityReporter.MetadataType.LINK)
  }

  override fun reportTestFailure(
    testName: String, message: String, details: String, linkToLogs: String?,
    kind: SyntheticTestKind, generifyTestName: Boolean
  ) {
    val metadata = buildList {
      linkToLogs?.let { add(TestMetadata(name = "Link to Logs and artifacts", value = it, type = TeamCityReporter.MetadataType.LINK)) }
      CurrentTestMethod.get()?.let { add(TestMetadata(name = "Test name", value = it.fullName())) }
      if (isJetbrainsBuildserver) {
        add(bisectMetadata())
      }
    }
    TeamCityReporter.reportTestLifecycle(testName, TestOutcome.FAILED, message, details,
                                         owner = codeOwnerResolver.getOwnerGroupName(),
                                         metadata = metadata, syntheticTestKind = kind, generifyTestName = generifyTestName)
  }

  override fun ignoreTestFailure(
    testName: String, message: String, details: String?,
    kind: SyntheticTestKind,
  ) {
    val metadata = details?.let { listOf(TestMetadata(name = "Details", value = it)) } ?: emptyList()
    TeamCityReporter.reportTestLifecycle(testName, TestOutcome.IGNORED, message, metadata = metadata, syntheticTestKind = kind)
  }

  override fun isTestFailureShouldBeIgnored(message: String): Boolean {
    getListOfPatternsWhichShouldBeIgnored().forEach { pattern ->
      if (pattern.containsMatchIn(message)) {
        return true
      }
    }
    return false
  }

  val buildStartTime: String by lazy {
    if (buildId == LOCAL_RUN_ID) {
      ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME)
    }
    else {
      val fullUrl = TeamCityClient.restUri.resolve("builds/id:${buildId}?fields=startDate")
      TeamCityClient.get(fullUrl) { it.withAuth() }.properties().firstOrNull { it.key == "startDate" }?.value?.asText()?.let {
        runCatching {
          ZonedDateTime.parse(it, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX")).format(DateTimeFormatter.RFC_1123_DATE_TIME)
        }.getOrNull()
      }
      ?: ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME)
    }
  }

  private fun getListOfPatternsWhichShouldBeIgnored(): MutableList<Regex> {
    val ignoredPattern = ConfigurationStorage.ignoredTestFailuresPattern()
    logOutput("DEBUG: ignored patterns from ENV $ignoredPattern")
    val patterns = mutableListOf(
      "No files have been downloaded for .+:.+".toRegex(),
      "Library '.+' resolution failed".toRegex(),
      "Too many IDE internal errors. Monitoring stopped.".toRegex(),
      "Invalid folding descriptor detected".toRegex(),
      "Non-idempotent computation: it returns different results when invoked multiple times".toRegex(),
      //RDCT-1508
      "current modality=ModalityState:.+com.intellij.openapi.ui.impl.DialogWrapperPeerImpl".toRegex(),
      //QD-9242
      "Descriptions are missed for tools: DevContainerIdeSettings".toRegex(),
    )
    if (ignoredPattern != null && ignoredPattern.isNotBlank()) {
      val ignoredPatterns = ignoredPattern.split("\n")
      ignoredPatterns.forEach {
        logOutput("Add $it ignored pattern from env")
        patterns.add(it.toRegex())
      }
    }
    return patterns
  }

  private fun loadProperties(propertiesPath: Path): Map<String, String> =
    try {
      propertiesPath.bufferedReader().use {
        val map = mutableMapOf<String, String>()
        val ps = Properties()
        ps.load(it)

        ps.forEach { k, v ->
          if (k != null && v != null) {
            map[k.toString()] = v.toString()
          }
        }
        map
      }
    }
    catch (t: Throwable) {
      emptyMap()
    }

  private val systemProperties by lazy {
    val props = mutableMapOf<String, String>()
    systemPropertiesFilePath?.let { props.putAll(loadProperties(it)) }

    props.putAll(System.getProperties().map { it.key.toString() to it.value.toString() })
    props
  }

  /**
   * @return String or Null if parameters aren't found
   */
  fun getBuildParam(name: String, impreciseNameMatch: Boolean = false): String? {
    val totalParams = systemProperties.plus(buildParams)

    val paramValue = if (impreciseNameMatch) {
      val paramCandidates = totalParams.filter { it.key.contains(name) }
      if (paramCandidates.size > 1) System.err.println("Found many parameters matching $name. Candidates: $paramCandidates")
      paramCandidates[paramCandidates.toSortedMap().firstKey()]
    }
    else totalParams[name]

    return paramValue
  }

  override val isBuildRunningOnCI = System.getenv("TEAMCITY_VERSION") != null
  override val buildNumber by lazy { System.getenv("BUILD_NUMBER") ?: "" }
  override val branchName by lazy { buildParams["teamcity.build.branch"] ?: "" }

  val isJetbrainsBuildserver by lazy { getBuildParam("teamcity.serverUrl")?.contains("buildserver.labs.intellij.net") == true }
  val configurationName by lazy { getBuildParam("teamcity.buildConfName") }
  val buildVcsNumber by lazy { getBuildParam("build.vcs.number") ?: "Unknown" }
  override val buildParams by lazy {
    val configurationPropertiesFile = systemProperties["teamcity.configuration.properties.file"]

    if (configurationPropertiesFile.isNullOrBlank()) return@lazy emptyMap()
    loadProperties(Path(configurationPropertiesFile))
  }

  /** Root URI of the server */
  val serverUri: URI by lazy {
    return@lazy di.direct.instance<URI>(tag = "teamcity.uri")
  }

  val userName: String by lazy { getBuildParam("teamcity.auth.userId")!! }
  val password: String by lazy { getBuildParam("teamcity.auth.password")!! }

  private val isDefaultBranch by lazy {
    //see https://www.jetbrains.com/help/teamcity/predefined-build-parameters.html#PredefinedBuildParameters-Branch-RelatedParameters
    hasBooleanProperty("teamcity.build.branch.is_default", default = false)
  }

  val isPersonalBuild by lazy {
    getBuildParam("build.is.personal").equals("true", ignoreCase = true)
  }

  val buildId: String by lazy {
    getBuildParam("teamcity.build.id") ?: LOCAL_RUN_ID
  }
  val teamcityAgentName by lazy { buildParams["teamcity.agent.name"] ?: "" }
  val teamcityCloudProfile by lazy { getBuildParam("system.cloud.profile_id") }

  val buildTypeId: String? by lazy { getBuildParam("teamcity.buildType.id") }

  fun buildUrl(): String = "$serverUri/buildConfiguration/$buildTypeId/$buildId?buildTab=tests"

  val isSpecialBuild: Boolean
    get() {
      if (!isBuildRunningOnCI) {
        logOutput("[Metrics Publishing] Not running build on TeamCity => DISABLED")
        return true
      }

      if (isPersonalBuild) {
        logOutput("[Metrics Publishing] Personal builds are ignored => DISABLED")
        return true
      }

      if (!isDefaultBranch) {
        logOutput("[Metrics Publishing] Non default branches builds are ignored => DISABLED")
        return true
      }

      return false
    }

  private fun hasBooleanProperty(key: String, default: Boolean) = getBuildParam(key)?.equals("true", ignoreCase = true) ?: default

  fun isSafePush(): Boolean {
    val isSafePush = System.getenv("SAFE_PUSH")
    return (isSafePush != null && isSafePush == "true")
  }

  companion object {
    const val LOCAL_RUN_ID = "LOCAL_RUN_SNAPSHOT"
  }
}
