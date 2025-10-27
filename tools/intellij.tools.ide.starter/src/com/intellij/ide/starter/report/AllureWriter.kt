package com.intellij.ide.starter.report

import com.intellij.ide.starter.di.di
import io.qameta.allure.AllureResultsWriter
import io.qameta.allure.FileSystemResultsWriter
import io.qameta.allure.model.TestResult
import io.qameta.allure.model.TestResultContainer
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.InputStream

object AllureWriter : AllureResultsWriter {
  private val allurePath
    get() = di.direct.instance<AllurePath>()

  override fun write(testResult: TestResult?) {
    FileSystemResultsWriter(allurePath.reportDir()).write(testResult)
  }

  override fun write(testResultContainer: TestResultContainer?) {
    FileSystemResultsWriter(allurePath.reportDir()).write(testResultContainer)
  }

  override fun write(source: String?, attachment: InputStream?) {
    FileSystemResultsWriter(allurePath.reportDir()).write(source, attachment)
  }
}