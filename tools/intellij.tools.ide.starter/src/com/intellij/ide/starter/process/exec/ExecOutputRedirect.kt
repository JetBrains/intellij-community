package com.intellij.ide.starter.process.exec

import com.intellij.tools.ide.util.common.logOutput
import java.io.File
import java.io.PrintWriter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories

/**
 * Specifies how a child process' stdout or stderr must be redirected in the current process:
 * - [NoRedirect]
 * - [ToFile]
 * - [ToStdOut]
 */
sealed class ExecOutputRedirect {

  open fun open() = Unit

  open fun close() = Unit

  open fun redirectLine(line: String) = Unit

  abstract fun read(): String

  abstract override fun toString(): String

  protected fun reportOnStdoutIfNecessary(line: String) {
    // Propagate the IDE debugger attach service message.
    if (line.contains("Listening for transport dt_socket")) {
      println(line)
    }
  }

  object NoRedirect : ExecOutputRedirect() {
    override fun redirectLine(line: String) {
      reportOnStdoutIfNecessary(line)
    }

    override fun read() = ""

    override fun toString() = "ignored"
  }

  data class ToFile(val outputFile: File, private val reportDebugForAutoAttach: Boolean = true) : ExecOutputRedirect() {

    private var writer: PrintWriter? = null

    //Todo instead of scheduling once a second,
    // we can add some other ExecOutputRedirect, which will be a stream (possibly a Kotlin flow).
    // The process will write to it, and it will be possible to read from the other side on demand.
    private var hasChanges: Boolean = false
    private val scheduler = Executors.newScheduledThreadPool(1)

    override fun close() {
      writer?.apply {
        flushPendingChanges()
        scheduler.shutdown()
        close()
      }
    }

    override fun redirectLine(line: String) {
      if (reportDebugForAutoAttach) {
        reportOnStdoutIfNecessary(line)
      }

      initializeWriterIfNotInitialized().let {
        it.println(line)
        hasChanges = true
      }
    }

    private fun initializeWriterIfNotInitialized(): PrintWriter {
      return writer ?: run {
        outputFile.apply {
          toPath().parent.createDirectories()
          createNewFile()
        }
        outputFile.printWriter().also {
          writer = it
          scheduler.scheduleAtFixedRate({ it.flushPendingChanges() }, 1, 1, TimeUnit.SECONDS)
        }
      }
    }

    private fun PrintWriter.flushPendingChanges() {
      if (hasChanges) {
        flush()
        hasChanges = false
      }
    }

    override fun read(): String {
      if (!outputFile.exists()) {
        logOutput("File $outputFile doesn't exist")
        return ""
      }

      return outputFile.readText()
    }

    override fun toString() = "file $outputFile"
  }

  data class DelegatedWithPrefix(val prefix: String, private val delegate: ExecOutputRedirect): ExecOutputRedirect()  {
    override fun read(): String {
      return delegate.read()
    }

    override fun open() {
      delegate.open()
    }

    override fun close() {
      delegate.close()
    }

    override fun redirectLine(line: String) {
      delegate.redirectLine("$prefix $line")
    }

    override fun toString(): String = "$delegate with prefix '$prefix'"
  }

  data class ToStdOut(val prefix: String) : ExecOutputRedirect() {
    override fun redirectLine(line: String) {
      reportOnStdoutIfNecessary(line)
      logOutput("  $prefix $line")
    }

    override fun read() = ""

    override fun toString() = "stdout"
  }

  data class ToStdOutAndString(val prefix: String) : ExecOutputRedirect() {
    private val stringBuilder = StringBuilder()

    override fun redirectLine(line: String) {
      reportOnStdoutIfNecessary(line)
      logOutput("  $prefix $line")
      stringBuilder.appendLine("$prefix $line")
    }

    override fun read() = stringBuilder.toString()

    override fun toString() = "stdout"
  }

  class ToString : ExecOutputRedirect() {

    private val stringBuilder = StringBuilder()

    override fun redirectLine(line: String) {
      reportOnStdoutIfNecessary(line)
      stringBuilder.appendLine(line)
    }

    override fun read() = stringBuilder.toString()

    override fun toString() = "string"
  }
}