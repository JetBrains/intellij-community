package com.intellij.java.lomboktest

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.openapi.command.executeCommand
import com.intellij.refactoring.suggested.LightJavaCodeInsightFixtureTestCaseWithUtils
import com.intellij.testFramework.LightProjectDescriptor
import de.plushnikov.intellij.plugin.LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR

class LombokAddRuntimeExceptionToThrowsActionTest: LightJavaCodeInsightFixtureTestCaseWithUtils() {
  override fun getProjectDescriptor(): LightProjectDescriptor = LOMBOK_JAVA21_DESCRIPTOR

  override fun getBasePath(): String = "community/plugins/lombok/testData/intention/addExceptionToThrows"


  fun testOverridingSyntheticElement() = doTest()

  private fun doTest() {
    val name = getTestName(false)
    myFixture.configureByFile("before$name.java")
    val intention = myFixture.availableIntentions.singleOrNull { it.familyName == QuickFixBundle.message("add.runtime.exception.to.throws.family") }
    assertNotNull(intention)
    executeCommand { intention?.invoke(project, editor, file) }
    myFixture.checkResultByFile("after$name.java")
  }
}
