package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.TestMethod
import com.intellij.ide.starter.utils.hyphenateTestName
import org.junit.jupiter.api.TestInfo

/**
 * Format: ClassName/testMethodName => class-name/test-method-name
 */
fun TestInfo.hyphenateWithClass(): String {

  val className = this.testClass.get().simpleName
  val methodName = this.testMethodName()

  return "$className/$methodName".hyphenateTestName()
}

fun TestInfo.hyphenateName(): String {
  return testMethod.get().name.hyphenateTestName()
}

fun TestInfo.testMethodName(): String {
  return if (this.testMethod.isPresent) testMethod.get().name
  else CurrentTestMethod.get()?.name ?: ""
}

private fun checkTestMethodIsNotNull(testMethod: TestMethod?): TestMethod = requireNotNull(testMethod) {
  "Cannot find current test method. Most likely you need to provide testName manually in the test context"
}

fun CurrentTestMethod.getName(): String = checkTestMethodIsNotNull(this.get()).name

/**
 * @return Hyphenated test name from the current test method as testMethodName => test-method-name
 */
fun CurrentTestMethod.hyphenateName(): String = checkTestMethodIsNotNull(this.get()).name.hyphenateTestName()

/**
 * @return Hyphenated test class and test method name from the current test method as ClassName/testMethodName => class-name/test-method-name
 */
fun CurrentTestMethod.hyphenateWithClass(): String = qualifiedName().hyphenateTestName()


/**
 * @return Hyphenated test class and display method name which can be provided by @DisplayName or @ParameterizedTest(name = ) or default just a method name
 */
fun CurrentTestMethod.displayName(): String = (CurrentTestMethod.className() + "/" + (get()?.displayName ?: "")).hyphenateTestName()

/**
 * Returns the qualified name of the current test method. Eg: `com.intellij.xyz.tests.ClassTest.testMethod`
 */
fun CurrentTestMethod.qualifiedName(): String {
  val method: TestMethod = checkTestMethodIsNotNull(this.get())
  return "${method.clazzSimpleName}/${method.name}${method.argsString()}"
}

/**
 * Returns the qualified name of the current test method. Eg: `com.intellij.xyz.tests.ClassTest.testMethod`
 */
fun CurrentTestMethod.className(): String {
  val method: TestMethod = checkTestMethodIsNotNull(this.get())
  return method.clazzSimpleName
}