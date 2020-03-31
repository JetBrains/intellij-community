// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.tools

import com.intellij.util.io.copy
import com.intellij.util.io.delete
import com.jetbrains.python.psi.PyIndentUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * `typeshed` has one file for Python 2 and Python 3 builtins where differences are guarded with `if sys.version_info` checks.
 * PyCharm does not support such checks at the moment so we have to split this file and
 * put builtins into the corresponding `typeshed` directories.
 */
fun splitBuiltins(typeshed: Path) {
  val typeshedStdlib: Path = typeshed.resolve("stdlib").abs().normalize()
  val originalBuiltins: Path = typeshedStdlib.resolve(Paths.get("2and3/builtins.pyi"))

  extractPython2Builtins(typeshedStdlib, originalBuiltins)
  extractPython3Builtins(typeshedStdlib, originalBuiltins)

  originalBuiltins.delete()
  println("Removed: ${originalBuiltins.abs()}")
}

private fun extractPython2Builtins(typeshedStdlib: Path, originalBuiltins: Path) {
  val python2Builtins = typeshedStdlib.resolve(Paths.get("2/__builtin__.pyi"))
  extractBuiltins(originalBuiltins, python2Builtins, true)
  println("Created: ${python2Builtins.abs()}")

  val python2BuiltinsMirror = typeshedStdlib.resolve(Paths.get("2/builtins.pyi"))
  python2Builtins.copy(python2BuiltinsMirror)
  println("Copied: ${python2Builtins.abs()} to ${python2BuiltinsMirror.abs()}")
}

private fun extractPython3Builtins(typeshedStdlib: Path, originalBuiltins: Path) {
  val python3Builtins = typeshedStdlib.resolve(Paths.get("3/builtins.pyi"))
  extractBuiltins(originalBuiltins, python3Builtins, false)
  println("Created: ${python3Builtins.abs()}")
}

private fun extractBuiltins(originalBuiltins: Path, targetBuiltins: Path, py2: Boolean) {
  Files.newBufferedWriter(targetBuiltins).use { writer ->
    var state: ReadingState = DefaultReading(py2)

    Files
      .lines(originalBuiltins)
      .forEach {
        val (updatedLine, updatedState) = state.processLine(it)
        updatedLine?.let {
          writer.write(it)
          writer.newLine()
        }

        state = updatedState
      }
  }
}

private interface ReadingState {
  fun processLine(line: String): Pair<String?, ReadingState>
}

private class DefaultReading(private val py2: Boolean) : ReadingState {

  override fun processLine(line: String): Pair<String?, ReadingState> = processLine(line, this)

  fun processLine(line: String, currentState: ReadingState): Pair<String?, ReadingState> {
    val indent = PyIndentUtil.getLineIndentSize(line)

    return when {
      disabledBlockStarted(line, indent, py2) -> null to ReadingConditionallyDisabledBlock(indent, py2)
      enabledBlockStarted(line, indent, py2) -> null to ReadingConditionallyEnabledBlock(indent, py2)
      line.startsWith("else:", indent) -> when (currentState) {
        is ReadingConditionallyEnabledBlock -> null to ReadingConditionallyDisabledBlock(indent, py2)
        is ReadingConditionallyDisabledBlock -> null to ReadingConditionallyEnabledBlock(indent, py2)
        else -> line to currentState
      }
      else -> line to this
    }
  }

  private fun disabledBlockStarted(line: String, indent: Int, py2: Boolean): Boolean {
    if (py2) {
      if (line.startsWith("if sys.version_info > (3,", indent)) return true
      if (line.startsWith("if sys.version_info >= (3,", indent)) return true

      if (line.startsWith("elif sys.version_info > (3,", indent)) return true
      if (line.startsWith("elif sys.version_info >= (3,", indent)) return true
    }
    else {
      if (line.startsWith("if sys.version_info < (3,)", indent)) return true
      if (line.startsWith("if sys.version_info <= (3,)", indent)) return true

      if (line.startsWith("elif sys.version_info < (3,)", indent)) return true
      if (line.startsWith("elif sys.version_info <= (3,)", indent)) return true
    }

    return false
  }

  private fun enabledBlockStarted(line: String, indent: Int, py2: Boolean): Boolean {
    if (py2) {
      if (line.startsWith("if sys.version_info < (3,)", indent)) return true
      if (line.startsWith("elif sys.version_info < (3,)", indent)) return true
    }
    else {
      if (line.startsWith("if sys.version_info > (3,)", indent)) return true
      if (line.startsWith("if sys.version_info >= (3,)", indent)) return true

      if (line.startsWith("elif sys.version_info > (3,)", indent)) return true
      if (line.startsWith("elif sys.version_info >= (3,)", indent)) return true
    }

    return false
  }
}

private class ReadingConditionallyDisabledBlock(private val guardIndent: Int, private val py2: Boolean) : ReadingState {

  override fun processLine(line: String): Pair<String?, ReadingState> {
    return when {
      line.isBlank() -> line to this
      PyIndentUtil.getLineIndentSize(line) <= guardIndent -> DefaultReading(py2).processLine(line, this)
      else -> null to this
    }
  }
}

private class ReadingConditionallyEnabledBlock(private val guardIndent: Int, private val py2: Boolean) : ReadingState {

  private var indentDifference: Int = 0

  override fun processLine(line: String): Pair<String?, ReadingState> {
    val currentIndent = PyIndentUtil.getLineIndentSize(line)

    return when {
      line.isBlank() -> line to this
      currentIndent <= guardIndent -> DefaultReading(py2).processLine(line, this)
      else -> {
        if (indentDifference == 0) indentDifference = currentIndent - guardIndent
        line.substring(indentDifference) to this
      }
    }
  }
}

private fun Path.abs() = toAbsolutePath()

