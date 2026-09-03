// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.runner.TestAborter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.opentest4j.TestAbortedException
import java.util.ServiceLoader

class JUnit5TestAborterTest {

  @Test
  fun `the aborter throws the exception JUnit 5 reads as a skip`() {
    val cause = IllegalStateException("the remote is down")

    val aborted = shouldThrow<TestAbortedException> { JUnit5TestAborter().abort("skip me", cause) }

    aborted.message shouldBe "skip me"
    aborted.cause shouldBe cause
  }

  @Test
  fun `the aborter is registered for service loading`() {
    val loaded = ServiceLoader.load(TestAborter::class.java, TestAborter::class.java.classLoader).toList()

    loaded.map { it::class } shouldBe listOf(JUnit5TestAborter::class)
  }
}
