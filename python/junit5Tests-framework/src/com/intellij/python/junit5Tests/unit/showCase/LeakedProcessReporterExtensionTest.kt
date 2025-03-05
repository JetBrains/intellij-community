package com.intellij.python.junit5Tests.unit.showCase

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.python.junit5Tests.framework.LeakedProcessReporterExtension
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(LeakedProcessReporterExtension::class)
class LeakedProcessReporterExtensionTest {
  private companion object {
    lateinit var process: Process

    @AfterAll
    @JvmStatic
    fun tearDown() {
      process.destroyForcibly()
    }
  }

  /**
   * `WARN` should be reported
   */
  @Test
  fun exampleTest() {
    val binary = if (SystemInfoRt.isWindows) "cmd.exe" else "/bin/sh"
    process = ProcessBuilder(binary).start()
  }
}