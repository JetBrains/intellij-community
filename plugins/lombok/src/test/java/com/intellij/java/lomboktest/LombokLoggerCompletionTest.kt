package com.intellij.java.lomboktest

import com.intellij.codeInsight.completion.JvmLoggerLookupElement
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.java.codeInsight.JvmLoggerTestSetupUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.NeedsIndex
import de.plushnikov.intellij.plugin.LombokTestUtil
import de.plushnikov.intellij.plugin.logging.LombokLoggingUtils

class LombokLoggerCompletionTest : LightFixtureCompletionTestCase() {

  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testSlf4j() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)

    doTest(LombokLoggingUtils.ID_LOMBOK_SLF_4_J, "long", "log", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testLog4j2() {
    JvmLoggerTestSetupUtil.setupLog4j2(myFixture)

    doTest(LombokLoggingUtils.ID_LOMBOK_LOG_4_J_2, "long", "log", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testLog4j() {
    JvmLoggerTestSetupUtil.setupLog4j(myFixture)

    doTest(LombokLoggingUtils.ID_LOMBOK_LOG_4_J, "long", "log", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testApacheCommons() {
    JvmLoggerTestSetupUtil.setupApacheCommons(myFixture)

    doTest(LombokLoggingUtils.ID_LOMBOK_APACHE_COMMONS_LOGGING, "long", "log", "log", "clone")
  }

  override fun getBasePath(): String = "community/plugins/lombok/testData/completion/logger"

  override fun getProjectDescriptor(): LightProjectDescriptor = LombokTestUtil.LOMBOK_OLD_JAVA_1_8_DESCRIPTOR

  private fun doTest(typeName: String, vararg names: String) {
    val name = getTestName(false)
    configureByFile("before$name.java")
    assertStringItems(*names)

    val item = lookup.items.find { it is JvmLoggerLookupElement && it.typeId == typeName }
    assertNotNull(item)
    selectItem(item)
    checkResultByFile("after$name.java")
  }
}