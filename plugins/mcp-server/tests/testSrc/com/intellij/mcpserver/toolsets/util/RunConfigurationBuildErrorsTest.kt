package com.intellij.mcpserver.toolsets.util

import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.FilePosition
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.StartBuildEvent
import com.intellij.build.events.impl.FailureImpl
import com.intellij.build.events.impl.FailureResultImpl
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
class RunConfigurationBuildErrorsTest {
  @Test
  fun `collects compiler details and ignores other builds and warnings`() {
    val errors = RunConfigurationBuildErrors { 42L }
    errors.onEvent("unrelated", StartBuildEvent.builder("Build", DefaultBuildDescriptor(43L, "Build", "/project", 0)).build())
    errors.onEvent("unrelated", MessageEvent.builder("Unrelated failure", MessageEvent.Kind.ERROR).build())
    errors.onEvent("ours", StartBuildEvent.builder("Build", DefaultBuildDescriptor(42L, "Build", "/project", 0)).build())
    errors.onEvent("ours", MessageEvent.builder("Warning", MessageEvent.Kind.WARNING).build())
    val source = File("/project/Main.java")
    val error = MessageEvent.builder("Cannot find symbol", MessageEvent.Kind.ERROR)
      .withDescription("Cannot find symbol: MissingDependency")
      .withFilePosition(FilePosition(source, 6, 2))
      .build()
    errors.onEvent("ours", error)
    errors.onEvent("ours", error)

    assertEquals("${source.path}:7: Cannot find symbol: MissingDependency", errors.getErrorText())
  }

  @Test
  fun `matches external builds by execution environment and retains nested failures`() {
    val environment = ExecutionEnvironment()
    environment.executionId = 42L
    val errors = RunConfigurationBuildErrors { 42L }
    val descriptor = DefaultBuildDescriptor("external-task", "Build", "/project", 0).withExecutionEnvironment(environment)
    errors.onEvent("external-task", StartBuildEvent.builder("Build", descriptor).build())
    val cause = FailureImpl("Dependency missing", "Cannot resolve example:library:1.0")
    val failure = FailureImpl("Task failed", null, listOf(cause))
    errors.onEvent("external-task", FinishBuildEvent.builder("external-task", "Failed", FailureResultImpl(listOf(failure))).build())

    assertEquals("Task failed\nCannot resolve example:library:1.0", errors.getErrorText())
  }

  @Test
  fun `does not match an unassigned execution id`() {
    val errors = RunConfigurationBuildErrors { 0L }
    errors.onEvent(0L, StartBuildEvent.builder("Build", DefaultBuildDescriptor(0L, "Build", "/project", 0)).build())
    errors.onEvent(0L, MessageEvent.builder("Unrelated failure", MessageEvent.Kind.ERROR).build())
    assertNull(errors.getErrorText())
  }

  @Test
  fun `collects plain error messages without treating successful tasks as failures`() {
    val errors = RunConfigurationBuildErrors { 42L }
    errors.onEvent(42L, StartBuildEvent.builder("Build", DefaultBuildDescriptor(42L, "Build", "/project", 0)).build())
    errors.onEvent(42L, FinishBuildEvent.builder(42L, "Finished", SuccessResultImpl()).build())
    assertNull(errors.getErrorText())
    errors.onEvent(42L, MessageEvent.builder("Compiler executable not found", MessageEvent.Kind.ERROR).build())
    assertEquals("Compiler executable not found", errors.getErrorText())
  }

  @Test
  fun `retains errors when a build failure has no message`() {
    val errors = RunConfigurationBuildErrors { 42L }
    errors.onEvent(42L, StartBuildEvent.builder("Build", DefaultBuildDescriptor(42L, "Build", "/project", 0)).build())
    val failure = FailureResultImpl(IllegalStateException("Cannot launch compiler", IllegalArgumentException("Invalid compiler path")))
    errors.onEvent(42L, FinishBuildEvent.builder(42L, "Failed", failure).build())
    assertEquals("Cannot launch compiler\nInvalid compiler path", errors.getErrorText())
  }

  @Test
  fun `formats missing messages and cyclic causes`() {
    assertEquals("java.lang.IllegalStateException", runConfigurationFailureText(IllegalStateException()))
    assertEquals("Root cause", runConfigurationFailureText(IllegalStateException(" ", IllegalArgumentException("Root cause"))))
    val first = IllegalStateException("First cause")
    val second = IllegalStateException("Second cause", first)
    first.initCause(second)
    assertEquals("First cause\nSecond cause", runConfigurationFailureText(first))
  }
}
