package com.intellij.ide.starter.report

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.teamcity.TeamCityCIServer
import com.intellij.ide.starter.ci.teamcity.asTeamCity
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.tools.ide.util.common.replaceSpecialCharactersWithHyphens
import java.net.URI
import java.net.URLEncoder

object DetailsForTeamcity : DetailsOnCI {
  override fun getDetails(runContext: IDERunContext, error: Error?): String {

    return if (CIServer.instance.isBuildRunningOnCI) {
      if (CIServer.instance.asTeamCity().isJetbrainsBuildserver) getFailureDetailsWithBisectLinkForCI(runContext, error)
      else getFailureDetailsForCI(runContext, error)
    }
    else getFailureDetailsForLocalRun(runContext, error)
  }

  fun getFailureDetailsForIgnoredTest(runContext: IDERunContext, error: Error?): String {
    return getFailureDetailsForCI(runContext, error)
  }

  private fun getFailureDetailsWithBisectLinkForCI(runContext: IDERunContext, error: Error?): String {
    val buildId = (CIServer.instance as? TeamCityCIServer)?.buildId.takeIf { it != TeamCityCIServer.LOCAL_RUN_ID }
    return getFailureDetailsForCI(runContext, error) +
           (buildId?.let { System.lineSeparator() + "Link to bisect: https://ij-perf.labs.jb.gg/bisect/launcher?buildId=$it" } ?: "")
  }

  private fun getFailureDetailsForCI(runContext: IDERunContext, error: Error?): String {
    val uri = getLinkToCIArtifacts(runContext)
    return "Test: ${getActiveTestName(runContext, error)}" + System.lineSeparator() +
           "Link on TC artifacts $uri"
  }

  override fun getLinkToCIArtifacts(runContext: IDERunContext): String {
    val teamCityCI = CIServer.instance.asTeamCity()
    val urlString = "${teamCityCI.serverUri}/buildConfiguration/${teamCityCI.buildTypeId}/${teamCityCI.buildId}" +
                    "?buildTab=artifacts#" +
                    URLEncoder.encode("/" + runContext.contextName.replaceSpecialCharactersWithHyphens(), CharsetToolkit.UTF8)

    return URI(urlString).normalize().toString()
  }

  private fun getFailureDetailsForLocalRun(runContext: IDERunContext, error: Error?): String {
    return "Test: ${getActiveTestName(runContext, error)}" + System.lineSeparator() +
           "You can find logs and other info under the path ${runContext.logsDir.toRealPath()}"
  }
}