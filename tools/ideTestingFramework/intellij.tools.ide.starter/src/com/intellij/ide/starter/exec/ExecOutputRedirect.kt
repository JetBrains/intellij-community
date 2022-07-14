package com.intellij.ide.starter.exec

import com.intellij.ide.starter.exec.ExecOutputRedirect.*
import com.intellij.ide.starter.utils.logOutput
import java.io.File
import java.io.PrintWriter
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

  data class ToFile(val outputFile: File) : ExecOutputRedirect() {

    private lateinit var writer: PrintWriter

    override fun open() {
      outputFile.apply {
        toPath().parent.createDirectories()
        createNewFile()
      }
      writer = outputFile.printWriter()
    }

    override fun close() {
      writer.close()
    }

    override fun redirectLine(line: String) {
      reportOnStdoutIfNecessary(line)
      writer.println(line)
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

  data class ToStdOut(val prefix: String) : ExecOutputRedirect() {
    override fun redirectLine(line: String) {
      reportOnStdoutIfNecessary(line)
      logOutput("  $prefix $line")
    }

    override fun read() = ""

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