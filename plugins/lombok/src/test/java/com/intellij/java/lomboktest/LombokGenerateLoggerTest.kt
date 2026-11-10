package com.intellij.java.lomboktest

import com.intellij.codeInsight.generation.GenerateLoggerHandler
import com.intellij.codeInsight.generation.ui.ChooseLoggerDialogWrapper
import com.intellij.java.codeInsight.JvmLoggerTestSetupUtil
import com.intellij.lang.logging.JvmLogger
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.UiInterceptors
import de.plushnikov.intellij.plugin.LombokTestUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class LombokGenerateLoggerTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = LombokTestUtil.getTestDataPath() + "/intention/generateLogger"
  }

  @Test
  fun testSlf4j(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)

    doTest()
  }

  @Test
  fun testLog4j(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    JvmLoggerTestSetupUtil.setupLog4j(myFixture)

    doTest()
  }

  @Test
  fun testLog4j2(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    JvmLoggerTestSetupUtil.setupLog4j2(myFixture)

    doTest()
  }

  @Test
  fun testApacheCommons(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    JvmLoggerTestSetupUtil.setupApacheCommons(myFixture)

    doTest("Lombok Apache Commons Logging")
  }

  private suspend fun doTest() {
    doTest("Lombok ${myFixture.getTestName(false)}")
  }

  private suspend fun doTest(loggerId : String) {
    val name = myFixture.getTestName(false)
    myFixture.configureByFile("before$name.java")
    UiInterceptors.register(
      object : UiInterceptors.UiInterceptor<ChooseLoggerDialogWrapper>(ChooseLoggerDialogWrapper::class.java) {
        override fun doIntercept(component: ChooseLoggerDialogWrapper) {
          Disposer.register(myFixture.testRootDisposable, component.disposable)
          component.setComboBoxItem(JvmLogger.getLoggerById(loggerId)!!)
          component.close(DialogWrapper.OK_EXIT_CODE)
        }
      }
    )
    withContext(Dispatchers.EDT) {
      GenerateLoggerHandler().invoke(myFixture.project, myFixture.editor, myFixture.file)

      myFixture.checkResultByFile("after$name.java")
    }
  }
}