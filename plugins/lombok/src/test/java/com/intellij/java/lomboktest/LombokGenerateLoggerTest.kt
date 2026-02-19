package com.intellij.java.lomboktest

import com.intellij.codeInsight.generation.GenerateLoggerHandler
import com.intellij.codeInsight.generation.ui.ChooseLoggerDialogWrapper
import com.intellij.java.codeInsight.JvmLoggerTestSetupUtil
import com.intellij.lang.logging.JvmLogger
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.ui.UiInterceptors
import de.plushnikov.intellij.plugin.LombokTestUtil

class LombokGenerateLoggerTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR

  override fun getBasePath(): String = "community/plugins/lombok/testData/intention/generateLogger"

  fun testSlf4j() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)

    doTest()
  }

  fun testLog4j() {
    JvmLoggerTestSetupUtil.setupLog4j(myFixture)

    doTest()
  }

  fun testLog4j2() {
    JvmLoggerTestSetupUtil.setupLog4j2(myFixture)

    doTest()
  }

  fun testApacheCommons() {
    JvmLoggerTestSetupUtil.setupApacheCommons(myFixture)

    doTest("Lombok Apache Commons Logging")
  }

  private fun doTest() {
    doTest("Lombok ${getTestName(false)}")
  }

  private fun doTest(loggerId : String) {
    val name = getTestName(false)
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
    GenerateLoggerHandler().invoke(project, editor, file)

    myFixture.checkResultByFile("after$name.java")
  }
}