package com.intellij.ide.starter.runner

import java.time.LocalDateTime

data class TestMethod(
  val name: String,
  val displayName: String,
  val testClass: Class<*>,
  val startTime: LocalDateTime = LocalDateTime.now(),
  var arguments: List<Any> = emptyList(),
) {
  val clazzSimpleName: String = testClass.simpleName
  val clazz: String = testClass.name

  fun argsString(): String = arguments.takeIf { it.isNotEmpty() }?.joinToString(prefix = "(", postfix = ")", separator = " ") ?: ""

  fun fullName(): String {
    return "$clazz.$name${argsString()}"
  }
}

/**
 * Container that contains the current test method reference.
 * Method is provided by [com.intellij.ide.starter.junit5.CurrentTestMethodProvider]
 */
object CurrentTestMethod {
  @Volatile
  private var testMethod: TestMethod? = null

  fun set(method: TestMethod?) {
    testMethod = method
  }

  fun get(): TestMethod? {
    return testMethod
  }
}