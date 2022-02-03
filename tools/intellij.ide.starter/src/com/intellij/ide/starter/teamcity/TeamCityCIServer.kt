package com.intellij.ide.starter.teamcity

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.utils.generifyErrorMessage
import com.intellij.ide.starter.utils.logOutput
import java.io.File
import java.nio.file.Path
import java.util.*

object TeamCityCIServer : CIServer {
  private val systemProperties by lazy {
    loadProperties(System.getenv("TEAMCITY_BUILD_PROPERTIES_FILE"))
  }

  override val isBuildRunningOnCI = System.getenv("TEAMCITY_VERSION") != null

  override val buildNumber by lazy { System.getenv("BUILD_NUMBER") ?: "" }

  override val branchName by lazy { buildParams["teamcity.build.branch"] ?: "" }

  override val buildParams by lazy {
    loadProperties(systemProperties["teamcity.configuration.properties.file"])
  }

  override fun publishArtifact(source: Path, artifactPath: String, artifactName: String) {
    if (!isBuildRunningOnCI) return
    TeamCityClient.publishTeamCityArtifacts(source = source, artifactPath = artifactPath, artifactName = artifactName)
  }

  override fun reportTestFailure(testName: String, message: String, details: String) {
    val flowId = UUID.randomUUID().toString()

    val generifiedTestName = generifyErrorMessage(testName)

    logOutput(String.format("##teamcity[testStarted name='%s' flowId='%s']", generifiedTestName, flowId))
    logOutput(String.format(
      "##teamcity[testFailed name='%s' message='%s' details='%s' flowId='%s']",
      generifiedTestName, message.processStringForTC(), details.processStringForTC(), flowId
    ))
    logOutput(String.format("##teamcity[testFinished name='%s' flowId='%s']", generifiedTestName, flowId))
  }

  fun getExistingParameter(name: String): String {
    return buildParams[name] ?: error("Parameter $name is not specified in the build!")
  }

  private val isDefaultBranch by lazy {
    //see https://www.jetbrains.com/help/teamcity/predefined-build-parameters.html#PredefinedBuildParameters-Branch-RelatedParameters
    hasBooleanProperty("teamcity.build.branch.is_default", default = false)
  }

  val isPersonalBuild by lazy {
    systemProperties["build.is.personal"].equals("true", ignoreCase = true)
  }

  val buildId by lazy {
    buildParams["teamcity.build.id"] ?: run {
      require(!isBuildRunningOnCI)
      "LOCAL_RUN_SNAPSHOT"
    }
  }
  val teamcityAgentName by lazy { buildParams["teamcity.agent.name"] }
  val teamcityCloudProfile by lazy { buildParams["system.cloud.profile_id"] }

  val buildTypeId by lazy { systemProperties["teamcity.buildType.id"] }

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

  fun hasBooleanProperty(key: String, default: Boolean) = buildParams[key]?.equals("true", ignoreCase = true) ?: default

  fun setStatusTextPrefix(text: String) {
    logOutput(" ##teamcity[buildStatus text='$text {build.status.text}'] ")
  }

  fun reportTeamCityStatistics(key: String, value: Int) {
    logOutput(" ##teamcity[buildStatisticValue key='${key}' value='${value}']")
  }

  fun reportTeamCityStatistics(key: String, value: Long) {
    logOutput(" ##teamcity[buildStatisticValue key='${key}' value='${value}']")
  }

  private fun String.processStringForTC(): String {
    return this.substring(0, Math.min(7000, this.length))
      .replace("\\|", "||")
      .replace("\\[", "|[")
      .replace("]", "|]")
      .replace("\n", "|n")
      .replace("'", "|'")
      .replace("\r", "|r")
  }

  private fun loadProperties(file: String?) =
    try {
      File(file ?: throw Error("No file!")).bufferedReader().use {
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
}