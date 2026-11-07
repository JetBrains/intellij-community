package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.CurrentTestPlan
import com.intellij.ide.starter.runner.TestMethod
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import java.lang.reflect.Method
import java.util.Optional

class CurrentTestMethodProviderTest {
  @AfterEach
  fun tearDown() {
    CurrentTestMethod.set(null)
  }

  @Test
  fun `uses the declaring test class from the method source`() {
    CurrentTestMethodProvider().executionStarted(testIdentifier())

    CurrentTestMethod.get()?.testClass shouldBe SampleTestClass::class.java
    CurrentTestMethod.get()?.id shouldBe TEST_UNIQUE_ID
  }

  @Test
  fun `announces the method only once the test runner has opened the test`() {
    val provider = CurrentTestMethodProvider()
    val events = mutableListOf<String>()
    val listener: (TestMethod?) -> Unit = { method ->
      if (method != null) events.add("report links")
    }
    // this very test runs under the real provider, so drop its method to keep the immediate fire of the listener silent
    CurrentTestMethod.set(null)
    CurrentTestMethod.addOnChangeListener(listener)
    try {
      provider.executionStarted(testIdentifier())
      events.add("TeamCity test started")

      provider.beforeEach(mock(ExtensionContext::class.java))

      events shouldBe listOf("TeamCity test started", "report links")
    }
    finally {
      CurrentTestMethod.removeOnChangeListener(listener)
    }
  }

  @Test
  fun `a test without the annotation is no performance test`() {
    CurrentTestMethodProvider().executionStarted(testIdentifier())

    CurrentTestMethod.get()?.isPerformanceTest shouldBe false
  }

  @Test
  fun `an annotated method is a performance test`() {
    CurrentTestMethodProvider().executionStarted(testIdentifier(SampleTestClass::class.java.getDeclaredMethod("performanceTestMethod")))

    CurrentTestMethod.get()?.isPerformanceTest shouldBe true
  }

  @Test
  fun `a method of an annotated class is a performance test`() {
    CurrentTestMethodProvider().executionStarted(testIdentifier(SamplePerformanceTestClass::class.java.getDeclaredMethod("testMethod")))

    CurrentTestMethod.get()?.isPerformanceTest shouldBe true
  }

  @Test
  fun `starting a test plan opens a new generation`() {
    val before = CurrentTestPlan.generation

    val emptyTestPlan = LauncherFactory.create().discover(LauncherDiscoveryRequestBuilder.request().build())
    CurrentTestMethodProvider().testPlanExecutionStarted(emptyTestPlan)

    CurrentTestPlan.generation shouldBe before + 1
  }

  private fun testIdentifier(testMethod: Method = SampleTestClass::class.java.getDeclaredMethod("testMethod")): TestIdentifier {
    val methodSource = MethodSource.from(testMethod)
    val testIdentifier = mock(TestIdentifier::class.java)
    doReturn(true).`when`(testIdentifier).isTest
    doReturn(Optional.of<TestSource>(methodSource)).`when`(testIdentifier).source
    doReturn("test method").`when`(testIdentifier).displayName
    doReturn(TEST_UNIQUE_ID).`when`(testIdentifier).uniqueId
    return testIdentifier
  }

  private class SampleTestClass {
    @Suppress("unused")
    fun testMethod() {
    }

    @PerformanceTest
    @Suppress("unused")
    fun performanceTestMethod() {
    }
  }

  @PerformanceTest
  private class SamplePerformanceTestClass {
    @Suppress("unused")
    fun testMethod() {
    }
  }

  companion object {
    private const val TEST_UNIQUE_ID = "[engine:junit-jupiter]/[class:sample.SampleTestClass]/[method:testMethod()]"
  }
}
