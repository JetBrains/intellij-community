package com.intellij.ide.starter.junit4

import com.intellij.ide.starter.utils.hyphenateTestName
import org.junit.rules.TestName
import kotlin.reflect.KClass

/**
 * Format: testMethodName => test-method-name
 */
fun TestName.hyphenate(): String {
  return this.methodName.hyphenateTestName()
}

/**
 * Format: ClassName/testMethodName => class-name/test-method-name
 */
fun TestName.hyphenateWithClass(clazz: KClass<*>): String {
  return "${clazz.simpleName}/${this.methodName}".hyphenateTestName()
}
