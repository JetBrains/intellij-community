package com.intellij.ide.starter.tests.examples.junit4

import org.junit.rules.TestName
import kotlin.reflect.KClass

/**
 * Format: testMethodName => test-method-name
 */
fun TestName.toPrintable(): String {
  return this.methodName.toPrintableTestName()
}

/**
 * Format: ClassName/testMethodName => class-name/test-method-name
 */
fun TestName.toPrintableWithClass(clazz: KClass<*>): String {
  return "${clazz.simpleName}/${this.methodName}".toPrintableTestName()
}

/**
 * Format: testMethodName => test-method-name
 */
fun String.toPrintableTestName(): String {
  return this.replace(" ", "-").trim().decapitalize().toCharArray()
    .map {
      if (it.isUpperCase()) "-${it.toLowerCase()}"
      else it
    }
    .joinToString(separator = "")
}