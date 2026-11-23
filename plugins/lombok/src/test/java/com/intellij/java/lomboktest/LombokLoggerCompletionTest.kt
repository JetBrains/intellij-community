package com.intellij.java.lomboktest

import com.intellij.codeInsight.completion.JvmLoggerLookupElement
import com.intellij.codeInsight.completion.lightCompletionFixture
import com.intellij.java.codeInsight.JvmLoggerTestSetupUtil
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import de.plushnikov.intellij.plugin.LombokTestUtil
import de.plushnikov.intellij.plugin.logging.LombokLoggingUtils
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@TestApplication
class LombokLoggerCompletionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_OLD_JAVA_1_8_DESCRIPTOR)
  }

  private val completion by lightCompletionFixture(projectFixture, LombokTestUtil.getTestDataPath() + "/completion/logger")
  private val myFixture get() = completion.fixture

  @Test
  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testSlf4j(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)

    doTest(LombokLoggingUtils.ID_LOMBOK_SLF_4_J, "long", "log", "log", "clone")
  }

  @Test
  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testLog4j2(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupLog4j2(myFixture)

    doTest(LombokLoggingUtils.ID_LOMBOK_LOG_4_J_2, "long", "log", "log", "clone")
  }

  @Test
  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testLog4j(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupLog4j(myFixture)

    doTest(LombokLoggingUtils.ID_LOMBOK_LOG_4_J, "long", "log", "log", "clone")
  }

  @Test
  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testApacheCommons(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupApacheCommons(myFixture)

    doTest(LombokLoggingUtils.ID_LOMBOK_APACHE_COMMONS_LOGGING, "long", "log", "log", "clone")
  }

  private fun doTest(typeName: String, vararg names: String) {
    val name = completion.getTestName(false)
    completion.configureByFile("before$name.java")
    completion.assertStringItems(*names)

    val item = completion.getLookup()!!.items.find { it is JvmLoggerLookupElement && it.typeId == typeName }
    assertNotNull(item)
    completion.selectItem(item!!)
    completion.checkResultByFile("after$name.java")
  }
}