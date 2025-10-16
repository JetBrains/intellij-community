package com.intellij.ide.starter.report

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.teamcity.TeamCityCIServer
import com.intellij.ide.starter.ci.teamcity.asTeamCity
import com.intellij.ide.starter.report.FailureDetailsOnCI.Companion.getTestMethodName
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.replaceSpecialCharactersWithHyphens
import com.intellij.openapi.vfs.CharsetToolkit
import java.net.URI
import java.net.URLEncoder

object FailureDetailsForTeamcity : FailureDetailsOnCI {
  override fun getFailureDetails(runContext: IDERunContext): String {

    return if (CIServer.instance.isBuildRunningOnCI) {
      if (CIServer.instance.asTeamCity().isJetbrainsBuildserver) getFailureDetailsWithBisectLinkForCI(runContext)
      else getFailureDetailsForCI(runContext)
    }
    else getFailureDetailsForLocalRun(runContext)
  }

  fun getFailureDetailsForIgnoredTest(runContext: IDERunContext): String {
    return getFailureDetailsForCI(runContext)
  }

  private fun getFailureDetailsWithBisectLinkForCI(runContext: IDERunContext): String {
    val buildId = (CIServer.instance as? TeamCityCIServer)?.buildId.takeIf { it != TeamCityCIServer.LOCAL_RUN_ID }
    return getFailureDetailsForCI(runContext) +
           (buildId?.let { System.lineSeparator() + "Link to bisect: https://ij-perf.labs.jb.gg/bisect/launcher?buildId=$it" } ?: "")
  }

  private fun getFailureDetailsForCI(runContext: IDERunContext): String {
    val testMethodName = getTestMethodName().ifEmpty { runContext.contextName }
    val uri = getLinkToCIArtifacts(runContext)
    return "Test: $testMethodName" + System.lineSeparator() +
           "You can find logs and other info in CI artifacts under the path ${runContext.contextName}" + System.lineSeparator() +
           "Link on TC artifacts $uri"
  }

  override fun getLinkToCIArtifacts(runContext: IDERunContext): String {
    val teamCityCI = CIServer.instance.asTeamCity()
    val urlString = "${teamCityCI.serverUri}/buildConfiguration/${teamCityCI.buildTypeId}/${teamCityCI.buildId}" +
                    "?buildTab=artifacts#${URLEncoder.encode("/" + runContext.contextName.replaceSpecialCharactersWithHyphens(), CharsetToolkit.UTF8)}"
    return URI(urlString).normalize().toString()
  }

  private fun getFailureDetailsForLocalRun(runContext: IDERunContext): String {
    val testMethodName = getTestMethodName().ifEmpty { runContext.contextName }

    return "Test: $testMethodName" + System.lineSeparator() +
           "You can find logs and other info under the path ${runContext.logsDir.toRealPath()}"
  }
}