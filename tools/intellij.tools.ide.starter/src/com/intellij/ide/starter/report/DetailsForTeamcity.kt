package com.intellij.ide.starter.report

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.teamcity.TeamCityCIServer
import com.intellij.ide.starter.ci.teamcity.asTeamCity
import com.intellij.ide.starter.runner.IDEReportingData
import com.intellij.openapi.vfs.CharsetToolkit
import java.net.URI
import java.net.URLEncoder

object DetailsForTeamcity : DetailsOnCI {
  override fun getDetails(ideReportingData: IDEReportingData): String {

    return if (CIServer.instance.isBuildRunningOnCI) {
      if (CIServer.instance.asTeamCity().isJetbrainsBuildserver) {
        getFailureDetailsWithBisectLinkForCI(ideReportingData)
      }
      else {
        getFailureDetailsForCI(ideReportingData)
      }
    }
    else getFailureDetailsForLocalRun(ideReportingData)
  }

  fun getFailureDetailsForIgnoredTest(ideReportingData: IDEReportingData): String {
    return getFailureDetailsForCI(ideReportingData)
  }

  private fun getFailureDetailsWithBisectLinkForCI(ideReportingData: IDEReportingData): String {
    val buildId = (CIServer.instance as? TeamCityCIServer)?.buildId.takeIf { it != TeamCityCIServer.LOCAL_RUN_ID }
    return getFailureDetailsForCI(ideReportingData) +
           (buildId?.let { System.lineSeparator() + "Link to bisect: https://ij-perf.labs.jb.gg/bisect/launcher?buildId=$it" } ?: "")
  }

  private fun getFailureDetailsForCI(ideReportingData: IDEReportingData): String {
    val uri = getLinkToCIArtifacts(ideReportingData)
    return "Test: ${ideReportingData.humanReadableTestName}" + System.lineSeparator() +
           "Link on TC artifacts $uri"
  }

  override fun getLinkToCIArtifacts(ideReportingData: IDEReportingData): String {
    val teamCityCI = CIServer.instance.asTeamCity()
    val urlString = "${teamCityCI.serverUri}/buildConfiguration/${teamCityCI.buildTypeId}/${teamCityCI.buildId}" +
                    "?buildTab=artifacts#" +
                    URLEncoder.encode("/" + ideReportingData.artifactPath, CharsetToolkit.UTF8)

    return URI(urlString).normalize().toString()
  }

  private fun getFailureDetailsForLocalRun(ideReportingData: IDEReportingData): String {
    return "Test: ${ideReportingData.humanReadableTestName}" + System.lineSeparator() +
           "You can find logs and other info under the path ${ideReportingData.logsDir.toRealPath()}"
  }
}
