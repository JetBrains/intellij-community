package com.intellij.ide.starter.report

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.runner.IDEReportingData
import org.kodein.di.direct
import org.kodein.di.instance

interface DetailsOnCI {
  companion object {
    val instance: DetailsOnCI
      get() = di.direct.instance<DetailsOnCI>()
  }

  fun getDetails(ideReportingData: IDEReportingData): String =
    "Test: ${ideReportingData.humanReadableTestName}" + System.lineSeparator() +
    "You can find logs and other useful info in CI artifacts under the path ${ideReportingData.artifactPath}"

  fun getLinkToCIArtifacts(ideReportingData: IDEReportingData): String? = null
}