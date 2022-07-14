package com.intellij.ide.starter.tests.examples.junit5

import com.intellij.ide.starter.utils.hyphenateTestName
import org.junit.jupiter.api.TestInfo

/**
 * Format: ClassName/testMethodName => class-name/test-method-name
 */
fun TestInfo.hyphenateWithClass(): String {

  val className = this.testClass.get().simpleName
  val methodName = this.testMethod.get().name

  return "$className/$methodName".hyphenateTestName()
}