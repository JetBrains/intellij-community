package com.intellij.lambda.sampleTestsWithPlugin

import com.intellij.lambda.testFramework.junit.ExecuteInMonolithAndSplitMode
import com.intellij.openapi.application.ApplicationManager
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@ExecuteInMonolithAndSplitMode
class MixedExampleTest {

  //private var testName: String = ""
  //
  //@BeforeEach
  //fun setUp(info: TestInfo) {
  //  testName = info.displayName
  //}
  //
  //@TestTemplate
  //fun testTemplateTest(ide: BackgroundRunWithLambda) = runBlocking {
  //  ide.runNamedLambdaInBackend(SampleTest.Companion.HelloBackendOnlyLambda::class)
  //  ide.runNamedLambda(SampleTest.Companion.HelloFrontendOnlyLambda::class)
  //}
  //
  //@TestTemplate
  //fun simpleUnitTest() {
  //  println("${Date.from(Instant.now())} Hello from simpleUnitTest! ${testName}")
  //}
  //
  //@TestTemplate
  //fun anotherTest() {
  //  println("${Date.from(Instant.now())} Hello from anotherTest! ${testName}")
  //}
  //
  //@TestFactory
  //fun testFactoryTest() {
  //  ApplicationManager.getApplication().invokeAndWait { println("Test factory test: ${testName}") }
  //}

  @ParameterizedTest
  @ValueSource(strings = ["param1", "param2"])
  fun parametrizedTest1(param: String) {
    ApplicationManager.getApplication().invokeAndWait { println("Parameterized test 1: param $param") }
  }

  //private fun customDataProvider(): Stream<Arguments> {
  //  return Stream.of(
  //    Arguments.of("one", Any()),
  //    Arguments.of("==", Any()),
  //    Arguments.of("xx", Any()),
  //  )
  //}
  //
  //@ParameterizedTest
  //@MethodSource("customDataProvider")
  //fun parametrizedTest2(param1: String, param2: Any) {
  //  ApplicationManager.getApplication().invokeAndWait { println("Parameterized test 2: ${testName} param $param1 $param2") }
  //}
}