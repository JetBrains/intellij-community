package com.intellij.ide.starter.report

import com.intellij.ide.starter.ci.teamcity.TeamCityCIServer.Companion.processStringForTC
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.utils.convertToHashCodeWithOnlyLetters
import com.intellij.ide.starter.utils.generifyErrorMessage
import com.intellij.tools.ide.util.common.logError
import io.qameta.allure.Allure
import io.qameta.allure.model.Label
import io.qameta.allure.model.Link
import io.qameta.allure.model.Status
import io.qameta.allure.model.StatusDetails
import io.qameta.allure.model.TestResult
import java.util.UUID


data class AllureLink(val name: String, val url: String) {
  companion object {
    fun single(name: String, url: String): List<AllureLink> = listOf(AllureLink(name, url))
  }
}

object AllureReport {

  private val ignoreLabels = setOf("layer", "AS_ID")
  private val errorLink = Link()

  init {
    errorLink.name = "How to process exception"
    errorLink.url = "https://jb.gg/ide-test-errors"
  }

  fun reportFailure(
    contextName: String,
    message: String,
    originalStackTrace: String,
    links: List<AllureLink> = emptyList(),
    suffix: String = "Exception",
  ) {
    try {
      val parentContext = captureCurrentAllureContext()
      val currentTestMethodName = CurrentTestMethod.get()?.fullName()

      val formattedStackTrace = buildString {
        append(originalStackTrace)
        appendLine().appendLine()
        appendLine("ContextName: $contextName")
        append("TestName: $currentTestMethodName")
      }
      reportErrorInNewThread(parentContext, links, formattedStackTrace, suffix, contextName, message)
    }
    catch (e: Exception) {
      logError("Fail to write allure", e)
    }
  }

  private fun reportErrorInNewThread(
    parentContext: AllureContextSnapshot,
    links: List<AllureLink>,
    formattedStackTrace: String,
    suffix: String,
    contextName: String,
    message: String,
  ) {
    kotlin.concurrent.thread(start = true, isDaemon = false) {
      val uuid = UUID.randomUUID().toString()

      val lifecycle = Allure.getLifecycle()
      val result = TestResult().apply { this.uuid = uuid }

      lifecycle.scheduleTestCase(result)
      lifecycle.startTestCase(uuid)
      try {
        val errorLabels = parentContext.labels.filter { label -> !ignoreLabels.contains(label.name) }.toMutableList()
        val linksList = mutableListOf<Link>()

        for ((name, url) in links) {
          Allure.link(name, url)
          linksList.add(Link().setName(name).setUrl(url))
        }
        linksList.add(errorLink)
        errorLabels.add(Label().setName("layer").setValue("Exception"))
        errorLabels.add(Label().setName("AS_ID").setValue("-1"))
        val hash = convertToHashCodeWithOnlyLetters(generifyErrorMessage(formattedStackTrace.processStringForTC()).hashCode())
        Allure.getLifecycle().updateTestCase {
          it.status = Status.FAILED
          it.name = "$suffix in ${parentContext.testName.ifBlank { contextName }}"
          it.statusDetails = StatusDetails().setMessage(message).setTrace(formattedStackTrace)
          it.fullName = parentContext.fullName.ifBlank { contextName } + ".${hash}" + ".${suffix.lowercase()}"
          it.testCaseName = parentContext.testCaseName
          it.historyId = hash
          it.description = "IDE ${suffix} error that appears when running ${parentContext.fullName}"
          it.links = linksList
          it.labels = errorLabels
        }
      }
      finally {
        Allure.getLifecycle().stopTestCase(uuid)
        Allure.getLifecycle().writeTestCase(uuid)
      }
    }.join()
  }

  private fun captureCurrentAllureContext(): AllureContextSnapshot {
    var snapshot = AllureContextSnapshot(emptyList(), "", "", "")
    // using updateTestCase just to read is a bit of a hack, but necessary if no getter exists
    Allure.getLifecycle().updateTestCase {
      snapshot = AllureContextSnapshot(
        labels = it.labels.orEmpty(),
        testName = it.name.orEmpty(),
        fullName = it.fullName.orEmpty(),
        testCaseName = it.testCaseName.orEmpty()
      )
    }
    return snapshot
  }

  private data class AllureContextSnapshot(
    val labels: List<Label>,
    val testName: String,
    val fullName: String,
    val testCaseName: String,
  )
}