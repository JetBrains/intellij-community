package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.runner.CurrentTestMethod
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.TestIdentifier
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import java.util.Optional

class CurrentTestMethodProviderTest {
  @AfterEach
  fun tearDown() {
    CurrentTestMethod.set(null)
  }

  @Test
  fun `uses the declaring test class from the method source`() {
    val uniqueId = "[engine:junit-jupiter]/[class:sample.SampleTestClass]/[method:testMethod()]"
    val testMethod = SampleTestClass::class.java.getDeclaredMethod("testMethod")
    val methodSource = MethodSource.from(testMethod)
    val testIdentifier = mock(TestIdentifier::class.java)
    doReturn(true).`when`(testIdentifier).isTest
    doReturn(Optional.of<TestSource>(methodSource)).`when`(testIdentifier).source
    doReturn("test method").`when`(testIdentifier).displayName
    doReturn(uniqueId).`when`(testIdentifier).uniqueId

    CurrentTestMethodProvider().executionStarted(testIdentifier)

    CurrentTestMethod.get()?.testClass shouldBe SampleTestClass::class.java
    CurrentTestMethod.get()?.id shouldBe uniqueId
  }

  private class SampleTestClass {
    @Suppress("unused")
    fun testMethod() {
    }
  }
}
