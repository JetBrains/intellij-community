package com.intellij.ide.starter.report

import com.intellij.ide.starter.ci.teamcity.TeamCityCIServer.Companion.processStringForTC
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.utils.convertToHashCodeWithOnlyLetters
import com.intellij.ide.starter.utils.generifyErrorMessage
import com.intellij.tools.ide.util.common.logError
import io.qameta.allure.Allure
import io.qameta.allure.model.*
import java.util.*


object AllureReport {

  private val ignoreLabels = setOf("layer", "AS_ID")
  private val errorLink = Link()

  init {
    errorLink.name = "How to process exception"
    errorLink.url = "https://jb.gg/ide-test-errors"
  }

  fun reportFailure(contextName: String, message: String, originalStackTrace: String, link: String? = null, suffix: String = "Exception") {
    try {
      val uuid = UUID.randomUUID().toString()
      val stackTrace = "${originalStackTrace}${System.lineSeparator().repeat(2)}ContextName: ${contextName}${System.lineSeparator()}TestName: ${CurrentTestMethod.get()?.fullName()}"
      val result = TestResult()
      result.uuid = uuid
      //inherit labels from the main test case for the exception
      var labels: List<Label> = mutableListOf()

      var testName = ""
      var fullName = ""
      var testCaseName = ""
      Allure.getLifecycle().updateTestCase {
        labels = it?.labels.orEmpty()
        testName = it?.name.orEmpty()
        fullName = it?.fullName.orEmpty()
        testCaseName = it?.testCaseName.orEmpty()
      }
      Allure.getLifecycle().scheduleTestCase(result)
      Allure.getLifecycle().startTestCase(uuid)
      val errorLabels = labels.filter { label -> !ignoreLabels.contains(label.name) }.toMutableList()
      val linkToCi = Link()

      if (link != null) {
        Allure.link("CI server", link)
        linkToCi.name = "CI server"
        linkToCi.url = link
      }
      errorLabels.add(Label().setName("layer").setValue("Exception"))
      errorLabels.add(Label().setName("AS_ID").setValue("-1"))
      val hash = convertToHashCodeWithOnlyLetters(generifyErrorMessage(stackTrace.processStringForTC()).hashCode())
      Allure.getLifecycle().updateTestCase {
        it.status = Status.FAILED
        it.name = "$suffix in ${testName.ifBlank { contextName }}"
        it.statusDetails = StatusDetails().setMessage(message).setTrace(stackTrace)
        it.fullName = fullName.ifBlank { contextName } + ".${hash}" + ".${suffix.lowercase()}"
        it.testCaseName = testCaseName
        it.historyId = hash
        it.description = "IDE ${suffix} error that appears when running $fullName"
        it.links = listOf(errorLink, linkToCi)
        it.labels = errorLabels
      }
      Allure.getLifecycle().stopTestCase(uuid)
      Allure.getLifecycle().writeTestCase(uuid)
    }
    catch (e: Exception) {
      logError("Fail to write allure", e)
    }
  }
}