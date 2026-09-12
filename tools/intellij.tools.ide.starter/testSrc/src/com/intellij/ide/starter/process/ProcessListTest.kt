package com.intellij.ide.starter.process

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/** Runs the oshi FFM provider for real, so a broken native binding fails here and not in an IDE run. */
class ProcessListTest {
  @Test
  fun `the process list contains the current process`() {
    val currentPid = ProcessHandle.current().pid()

    val processes = runBlocking { getProcessList() }
    processes.shouldNotBeEmpty()
    val current = processes.single { it.pid == currentPid }
    current.name.shouldNotBeBlank()
    current.processHandle?.pid() shouldBe currentPid
  }

  @Test
  fun `a process is resolved by pid`() {
    val currentPid = ProcessHandle.current().pid()

    val current = runBlocking { ProcessInfo.create(currentPid) }
    current.pid shouldBe currentPid
    current.name.shouldNotBeBlank()
    current.arguments.shouldNotBeEmpty()
  }
}
