package com.intellij.java.lomboktest

import com.intellij.codeInsight.completion.JvmLoggerLookupElement
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.java.codeInsight.JvmLoggerTestSetupUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.NeedsIndex
import de.plushnikov.intellij.plugin.LombokTestUtil
import de.plushnikov.intellij.plugin.logging.LombokLoggingUtils
import junit.framework.TestCase

class LombokLoggerCompletionTest : LightFixtureCompletionTestCase() {

  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testSlf4j() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)

    doTest(LombokLoggingUtils.SLF4J_ANNOTATION, "long", "log", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testLog4j2() {
    JvmLoggerTestSetupUtil.setupLog4j2(myFixture)

    doTest(LombokLoggingUtils.LOG4J2_ANNOTATION, "long", "log", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testLog4j() {
    JvmLoggerTestSetupUtil.setupLog4j(myFixture)

    doTest(LombokLoggingUtils.LOG4J_ANNOTATION, "long", "log", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testApacheCommons() {
    JvmLoggerTestSetupUtil.setupApacheCommons(myFixture)

    doTest(LombokLoggingUtils.COMMONS_ANNOTATION, "long", "log", "log", "clone")
  }

  override fun getBasePath(): String = "community/plugins/lombok/testData/completion/logger"

  override fun getProjectDescriptor(): LightProjectDescriptor = LombokTestUtil.LOMBOK_OLD_DESCRIPTOR

  private fun doTest(typeName: String, vararg names: String) {
    val name = getTestName(false)
    configureByFile("before$name.java")
    assertStringItems(*names)

    val item = lookup.items.find { it is JvmLoggerLookupElement && it.typeName == typeName }
    TestCase.assertNotNull(item)
    selectItem(item)
    checkResultByFile("after$name.java")
  }
}