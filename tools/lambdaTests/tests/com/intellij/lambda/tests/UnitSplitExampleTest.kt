package com.intellij.lambda.tests

import com.intellij.lambda.testFramework.junit.ExecuteInMonolithAndSplitMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.remoteDev.tests.modelGenerated.LambdaRdIdeType
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junitpioneer.jupiter.cartesian.CartesianTest
import java.util.stream.Stream

@ExecuteInMonolithAndSplitMode
class UnitSplitExampleTest {
  // @Test annotation will not run the test in different modes, since it's by design executed only once

  @TestTemplate
  fun testTemplateTest() {
    ApplicationManager.getApplication().invokeAndWait { println("Test template test : badums") }
  }

  @TestFactory
  fun testFactoryTest() {
    ApplicationManager.getApplication().invokeAndWait { println("Test factory test : badums") }
  }

  @ParameterizedTest
  @ValueSource(strings = ["param1", "param2"])
  fun parametrizedTest(param: String) {
    ApplicationManager.getApplication().invokeAndWait { println("Parameterized test: Badums-$param") }
  }

  private fun customDataProvider(): Stream<String> {
    return Stream.of("one", "==", "xx")
  }

  @CartesianTest
  @CartesianTest.MethodFactory("customDataProvider")
  fun testSystemAccess(
    @CartesianTest.Enum(LambdaRdIdeType::class) mode: LambdaRdIdeType,
    data: String,
  ) {
    ApplicationManager.getApplication().invokeAndWait { println("Cartesian test: $mode $data") }
  }
}

