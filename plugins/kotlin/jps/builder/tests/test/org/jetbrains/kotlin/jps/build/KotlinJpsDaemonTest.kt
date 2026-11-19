// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.jps.build

import com.intellij.openapi.util.io.NioFiles
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.kotlin.cli.common.CompilerSystemProperties
import org.jetbrains.kotlin.daemon.common.COMPILE_DAEMON_DEFAULT_SHUTDOWN_DELAY_MS
import org.jetbrains.kotlin.jps.KotlinJpsBuilderBundle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries

/**
 * Builds a Kotlin module through a real daemon process, as the IDE runs the build with the "Enable Kotlin daemon"
 * setting.
 *
 * The daemon gets [DAEMON_RUN_DIRECTORY] and the default shutdown delay of the daemon. The delay of the builder would
 * keep the daemon alive long after the test.
 */
class KotlinJpsDaemonTest : AbstractKotlinJpsBuilderTest() {
  @BeforeEach
  fun enableDaemon() {
    CompilerSystemProperties.COMPILE_DAEMON_CUSTOM_RUN_FILES_PATH_FOR_TESTS.value = DAEMON_RUN_DIRECTORY.toString()
    CompilerSystemProperties.COMPILE_DAEMON_OPTIONS_PROPERTY.value = "shutdownDelayMilliseconds=$COMPILE_DAEMON_DEFAULT_SHUTDOWN_DELAY_MS"
    // The IDE passes the property without a value: its presence turns the daemon on
    CompilerSystemProperties.COMPILE_DAEMON_ENABLED_PROPERTY.value = ""
  }

  /**
   * Stops the daemon of the test. The daemon stops when its run file is gone. The test waits for the daemon process and
   * kills it after [DAEMON_STOP_TIMEOUT_SECONDS]. The next test cannot connect to the daemon, because its run file is gone.
   */
  @AfterEach
  fun stopDaemon() {
    CompilerSystemProperties.COMPILE_DAEMON_ENABLED_PROPERTY.clear()
    CompilerSystemProperties.COMPILE_DAEMON_JVM_OPTIONS_PROPERTY.clear()
    CompilerSystemProperties.COMPILE_DAEMON_OPTIONS_PROPERTY.clear()
    CompilerSystemProperties.COMPILE_DAEMON_CUSTOM_RUN_FILES_PATH_FOR_TESTS.clear()
    val directory = DAEMON_RUN_DIRECTORY
    if (!Files.isDirectory(directory)) return

    // The daemon gets the run directory on its command line. Without a command line in the process info, the test does not wait.
    val daemons = ProcessHandle.current().descendants()
      .filter { it.info().commandLine().orElse("").contains(directory.toString()) }
      .toList()
    directory.listDirectoryEntries("*.run").forEach { it.deleteIfExists() }
    for (daemon in daemons) {
      try {
        daemon.onExit().get(DAEMON_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      }
      catch (_: TimeoutException) {
        daemon.destroyForcibly()
      }
    }
    NioFiles.deleteRecursively(directory)
  }

  @Test
  fun testCompilesThroughDaemon() {
    createFile("src/a.kt", "package p\n\nfun a(): Int = 1\n")
    val module = addModuleOnModularJdk("m", getAbsolutePath("src"))
    addKotlinStdlib(module)

    val result = buildAllModules()
    result.assertSuccessful()
    assertTrue(File(getModuleOutput(module), "p/AKt.class").isFile)

    val infos = result.getMessages(BuildMessage.Kind.JPS_INFO).map { it.messageText }
    assertTrue(KotlinJpsBuilderBundle.message("info.compiling.through.kotlin.daemon") in infos)
    val warnings = result.getMessages(BuildMessage.Kind.WARNING).map { it.messageText }
    assertTrue(warnings.none { it.contains("Kotlin daemon failed") })
  }

  /**
   * A daemon that cannot start must not fail the build: the builder compiles in process again, as the legacy Kotlin JPS
   * plugin does.
   */
  @Test
  fun testFallsBackToInProcessWhenDaemonFails() {
    // An invalid JVM option makes the daemon process exit at startup
    CompilerSystemProperties.COMPILE_DAEMON_JVM_OPTIONS_PROPERTY.value = "-aaa"

    createFile("src/a.kt", "package p\n\nfun a(): Int = 1\n")
    val module = addModuleOnModularJdk("m", getAbsolutePath("src"))
    addKotlinStdlib(module)

    val result = buildAllModules()
    result.assertSuccessful()
    assertTrue(File(getModuleOutput(module), "p/AKt.class").isFile)

    val warnings = result.getMessages(BuildMessage.Kind.WARNING).map { it.messageText }
    assertTrue(KotlinJpsBuilderBundle.message("warning.kotlin.daemon.failed.0", "m") in warnings)
    assertTrue(result.getMessages(BuildMessage.Kind.ERROR).isEmpty())
  }

  private companion object {
    /** The daemon checks the run file each second and then stops after one more second. */
    const val DAEMON_STOP_TIMEOUT_SECONDS = 30L

    /**
     * The daemon run directory of the test. The implementation reads the directory once, with its first daemon policy,
     * so all daemon tests of the JVM share it. The daemon creates the directory again after [stopDaemon] deletes it.
     */
    val DAEMON_RUN_DIRECTORY: Path by lazy { Files.createTempDirectory("kotlin-daemon-run") }
  }
}
