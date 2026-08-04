package com.intellij.ide.starter.report

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.util.common.replaceSpecialCharactersWithHyphens
import org.kodein.di.direct
import org.kodein.di.instance

interface DetailsOnCI {
  companion object {
    val instance: DetailsOnCI
      get() = di.direct.instance<DetailsOnCI>()

    fun getActiveTestName(): String {
      val method = di.direct.instance<CurrentTestMethod>().get()
      return method?.fullName() ?: ""
    }
  }

  fun getDetails(runContext: IDERunContext, error: Error?): String =
    "Test: ${getActiveTestName(runContext, error)}" + System.lineSeparator() +
    "You can find logs and other useful info in CI artifacts under the path ${runContext.contextName.replaceSpecialCharactersWithHyphens()}"

  fun getActiveTestName(runContext: IDERunContext, error: Error?): String =
    error?.activeTestName ?: getActiveTestName().ifEmpty { runContext.contextName }

  fun getLinkToCIArtifacts(runContext: IDERunContext): String? = null
}