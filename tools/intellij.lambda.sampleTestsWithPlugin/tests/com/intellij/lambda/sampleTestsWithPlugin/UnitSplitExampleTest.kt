package com.intellij.lambda.sampleTestsWithPlugin

import com.intellij.lambda.testFramework.junit.RunInMonolithAndSplitMode
import com.intellij.openapi.application.ApplicationManager
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.*
import java.util.stream.Stream

@RunInMonolithAndSplitMode
class UnitSplitExampleTest {
  @TestTemplate
  fun simpleUnitTest() {
    println("${Date.from(Instant.now())} Hello from simpleUnitTest!")
  }

  @TestTemplate
  fun anotherTest() {
    println("${Date.from(Instant.now())} Hello from anotherTest!")
  }

  @ParameterizedTest
  @ValueSource(strings = ["param1", "param2"])
  fun parametrizedTest1(param: String) {
    ApplicationManager.getApplication().invokeAndWait { println("Parameterized test 1: param $param") }
  }

  private fun customDataProvider(): Stream<Arguments> {
    return Stream.of(
      Arguments.of("one", 1),
      Arguments.of("==", 2),
      Arguments.of("xx", 3),
    )
  }

  @ParameterizedTest
  @MethodSource("customDataProvider")
  fun parametrizedTest2(param1: String, param2: Int) {
    ApplicationManager.getApplication().invokeAndWait { println("Parameterized test 2: params $param1 $param2") }
  }
}

