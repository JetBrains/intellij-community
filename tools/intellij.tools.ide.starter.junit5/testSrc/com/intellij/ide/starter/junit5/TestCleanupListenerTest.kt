package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.di.di
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.launcher.TestIdentifier
import org.kodein.di.DI
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import java.util.Optional

class TestCleanupListenerTest {
  private val diOfThisTest: DI = di

  @AfterEach
  fun restoreDi() {
    di = diOfThisTest
  }

  @Test
  fun `a finished class gives back the DI of its start`() {
    val listener = TestCleanupListener()
    val sampleClass = classIdentifier("[class:Sample]")

    listener.executionStarted(sampleClass)
    di = DI {}
    listener.executionFinished(sampleClass, TestExecutionResult.successful())

    (di === diOfThisTest) shouldBe true
  }

  @Test
  fun `a finished nested class gives back the DI of the outer class`() {
    val listener = TestCleanupListener()
    val outerClass = classIdentifier("[class:Outer]")
    val nestedClass = classIdentifier("[class:Outer]/[nested-class:Nested]")

    listener.executionStarted(outerClass)
    val diOfOuterClass = DI {}
    di = diOfOuterClass
    listener.executionStarted(nestedClass)
    di = DI {}
    listener.executionFinished(nestedClass, TestExecutionResult.successful())

    (di === diOfOuterClass) shouldBe true

    listener.executionFinished(outerClass, TestExecutionResult.successful())

    (di === diOfThisTest) shouldBe true
  }

  private fun classIdentifier(uniqueId: String): TestIdentifier {
    val testIdentifier = mock(TestIdentifier::class.java)
    doReturn(false).`when`(testIdentifier).isTest
    doReturn(true).`when`(testIdentifier).isContainer
    doReturn(Optional.of<TestSource>(ClassSource.from(TestCleanupListenerTest::class.java))).`when`(testIdentifier).source
    doReturn(uniqueId).`when`(testIdentifier).displayName
    doReturn(uniqueId).`when`(testIdentifier).uniqueId
    return testIdentifier
  }
}
