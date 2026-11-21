// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.emulator.impl.ghostty.winStackCommitter

import com.intellij.terminal.emulator.TerminalEmulator
import com.intellij.terminal.emulator.TerminalGridGeometry
import com.intellij.terminal.emulator.TerminalListener
import com.intellij.terminal.emulator.TerminalSize
import com.intellij.terminal.emulator.createTerminalEmulator
import com.intellij.terminal.emulator.impl.ghostty.bindings.GHOSTTY_VT_LIB_ROOT_PROPERTY
import com.intellij.terminal.emulator.impl.ghostty.bindings.LibGhosttyVtLocator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URLClassLoader
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeLines
import kotlin.system.exitProcess

/**
 * The emulator must keep the JVM alive in the conditions of IJPL-261768.
 * See [com.intellij.terminal.emulator.impl.ghostty.GhosttyWindowsCallbackStackCommitter].
 *
 * The conditions stop the JVM, and they need the `-XX:SoftRefLRUPolicyMSPerMB=0` JVM option.
 * So the test runs [EmulatorThreadStackApp] in a child JVM. Without the workaround, the child JVM crashes.
 * A failure message has the start of the crash report.
 *
 * The test needs the JVM to reach the crash conditions, and it covers only `ghostty_terminal_vt_write`.
 * [GhosttyWindowsCallbackStackCommitterTest] does not have these limits.
 */
@EnabledOnOs(OS.WINDOWS)
class EmulatorThreadStackTest {
  @Test
  fun `a new thread survives the first callback of a new emulator`(@TempDir tempDir: Path) {
    val result = runApp(tempDir)
    assertThat(result.output).contains(DONE_MARKER)
    assertThat(result.exitCode).describedAs(result.output).isEqualTo(0)
  }

  private fun runApp(tempDir: Path): AppResult {
    val libRoot = LibGhosttyVtLocator.findLibraryFile().parent.parent
    val errorFile = tempDir.resolve("hs_err.log")
    val jvmArguments = listOf(
      // Without this option, the linker keeps its callback factory, and the JVM does not reach the conditions.
      "-XX:SoftRefLRUPolicyMSPerMB=0",
      // The JIT options of the IDE. They change the frame sizes of compiled code, and the committed stack size depends on them.
      "-XX:+IgnoreUnrecognizedVMOptions",
      "-XX:+UnlockDiagnosticVMOptions",
      "-XX:CICompilerCount=2",
      "-XX:TieredOldPercentage=100000",
      "-Xmx256m",
      "--enable-native-access=ALL-UNNAMED",
      "-XX:ErrorFile=$errorFile",
      "-D$GHOSTTY_VT_LIB_ROOT_PROPERTY=$libRoot",
      "-cp",
      testJvmClassPath(),
      EmulatorThreadStackApp::class.java.name,
    )
    // The class path is longer than a Windows command line, so the arguments go to a file.
    // The Java launcher reads the file in the native encoding.
    val argumentFile = tempDir.resolve("java.args")
    val nativeCharset = Charset.forName(System.getProperty("native.encoding"))
    argumentFile.writeLines(jvmArguments.map { "\"" + it.replace("\\", "\\\\") + "\"" }, nativeCharset)
    val outputFile = tempDir.resolve("output.txt")
    val java = Path.of(System.getProperty("java.home"), "bin", "java.exe")
    val process = ProcessBuilder(java.toString(), "@$argumentFile")
      .redirectErrorStream(true)
      .redirectOutput(outputFile.toFile())
      .start()
    if (!process.waitFor(5, TimeUnit.MINUTES)) {
      process.destroyForcibly()
      throw AssertionError("The child JVM did not stop in time:\n" + outputFile.readText())
    }
    val output = if (Files.exists(outputFile)) outputFile.readText() else ""
    // JUnit deletes the temporary directory, so the start of the crash report goes to the failure message.
    val crashReport = if (Files.exists(errorFile)) {
      "\n--- hs_err.log ---\n" + errorFile.readLines(Charsets.ISO_8859_1).take(CRASH_REPORT_LINES).joinToString("\n")
    }
    else {
      ""
    }
    return AppResult(process.exitValue(), output + crashReport)
  }

  /**
   * Returns the class path of this JVM. A test runner can load the tests through a class loader,
   * and then `java.class.path` holds only the classes of the runner.
   */
  private fun testJvmClassPath(): String = buildList {
    addAll(System.getProperty("java.class.path").split(File.pathSeparator))
    for (loader in generateSequence(javaClass.classLoader) { it.parent }) {
      if (loader is URLClassLoader) {
        // A class loader can also hold a URL that is not a file, such as a `jar:` URL.
        addAll(loader.urLs.filter { it.protocol == "file" }.map { Path.of(it.toURI()).toString() })
      }
      else {
        // `com.intellij.util.lang.UrlClassLoader` and `PathClassLoader` have this function.
        val getFiles = loader.javaClass.methods.singleOrNull { it.name == "getFiles" && it.parameterCount == 0 }
        val files = getFiles?.invoke(loader) as? List<*>
        if (files != null) addAll(files.map { it.toString() })
      }
    }
  }.distinct().joinToString(File.pathSeparator)

  private class AppResult(val exitCode: Int, val output: String)
}

/**
 * Repeats the start of a terminal session in the conditions of IJPL-261768.
 */
internal object EmulatorThreadStackApp {
  @JvmStatic
  fun main(args: Array<String>) {
    // The JIT compiler must compile the entry of the callback before the first attempt.
    val warmUp = CountingEmulator()
    runOnNewThread(depth = 0) {
      repeat(WARM_UP_QUERIES) { warmUp.query() }
    }
    check(warmUp.replies == WARM_UP_QUERIES) { "Expected $WARM_UP_QUERIES replies, got ${warmUp.replies}" }

    // The emulators stay open, as the sessions of the IDE do.
    val emulators = ArrayList<CountingEmulator>()
    repeat(ATTEMPTS) { attempt ->
      // Two collections make the linker drop its callback factory. The next emulator then gets new callback classes.
      System.gc()
      Thread.sleep(5)
      System.gc()
      val emulator = CountingEmulator()
      emulators.add(emulator)
      // The depth moves the stack pointer, because the fault depends on its offset in a memory page.
      runOnNewThread(depth = attempt % MAX_DEPTH) { emulator.query() }
      check(emulator.replies == 1) { "Expected 1 reply, got ${emulator.replies}" }
    }

    // A thread that made a shallow call must survive the first call of a new emulator from a deeper frame.
    repeat(ATTEMPTS) { attempt ->
      val shallow = CountingEmulator()
      emulators.add(shallow)
      var deep: CountingEmulator? = null
      runOnNewThread(depth = 0) {
        shallow.query()
        System.gc()
        Thread.sleep(5)
        System.gc()
        val emulator = CountingEmulator()
        deep = emulator
        atDepth(DEEP_CALL_DEPTH + attempt % MAX_DEPTH) { emulator.query() }
      }
      emulators.add(checkNotNull(deep))
      check(shallow.replies == 1) { "Expected 1 reply to the shallow call, got ${shallow.replies}" }
      check(deep.replies == 1) { "Expected 1 reply to the deep call, got ${deep.replies}" }
    }
    println(DONE_MARKER)
    // The emulators hold native memory only, so the JVM can stop without closing them.
    exitProcess(0)
  }

  private fun runOnNewThread(depth: Int, action: () -> Unit) {
    // A failure of the action leaves the reply count wrong, and the caller checks the count.
    val thread = Thread(null, { atDepth(depth, action) }, "emulator writer", THREAD_STACK_SIZE)
    thread.start()
    thread.join()
  }

  private fun atDepth(depth: Int, action: () -> Unit) {
    if (depth == 0) action() else atDepth(depth - 1, action)
  }

  private class CountingEmulator {
    private val emulator: TerminalEmulator = createTerminalEmulator(TerminalGridGeometry(TerminalSize(80, 24)))

    @Volatile
    var replies: Int = 0
      private set

    init {
      emulator.listener = object : TerminalListener {
        override fun onRespondToHost(data: ByteArray) {
          replies++
        }
      }
    }

    /** Sends the Primary Device Attributes query. The emulator answers it through [TerminalListener.onRespondToHost]. */
    fun query() {
      emulator.write(DEVICE_ATTRIBUTES_QUERY)
    }
  }

  private val DEVICE_ATTRIBUTES_QUERY: ByteArray = "\u001B[c".encodeToByteArray()
  private const val WARM_UP_QUERIES: Int = 50_000
  private const val ATTEMPTS: Int = 100
  private const val MAX_DEPTH: Int = 128

  /** Puts the deep call below the part of the stack that the shallow call committed. */
  private const val DEEP_CALL_DEPTH: Int = 50_000

  /** Holds [DEEP_CALL_DEPTH] frames also in the interpreter. */
  private const val THREAD_STACK_SIZE: Long = 64L * 1024 * 1024
}

private const val DONE_MARKER: String = "ALL ATTEMPTS PASSED"
private const val CRASH_REPORT_LINES: Int = 80
