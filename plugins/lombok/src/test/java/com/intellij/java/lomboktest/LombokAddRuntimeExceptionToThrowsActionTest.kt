package com.intellij.java.lomboktest

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.executeCommand
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import de.plushnikov.intellij.plugin.LombokTestUtil
import de.plushnikov.intellij.plugin.LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class LombokAddRuntimeExceptionToThrowsActionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LOMBOK_JAVA21_DESCRIPTOR)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = LombokTestUtil.getTestDataPath() + "/intention/addExceptionToThrows"
  }

  @Test
  fun testOverridingSyntheticElement(): Unit = doTest()

  private fun doTest(): Unit = timeoutRunBlocking {
    val name = myFixture.getTestName(false)
    myFixture.configureByFile("before$name.java")
    withContext(Dispatchers.EDT) {
      val intention = myFixture.availableIntentions.singleOrNull { it.familyName == QuickFixBundle.message("add.runtime.exception.to.throws.family") }
      assertNotNull(intention)
      executeCommand { intention?.invoke(myFixture.project, myFixture.editor, myFixture.file) }
      myFixture.checkResultByFile("after$name.java")
    }
  }
}
