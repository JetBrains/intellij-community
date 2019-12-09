// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console

import com.intellij.openapi.project.Project
import com.jetbrains.python.PythonParserDefinition
import com.jetbrains.python.parsing.PythonParsingTest
import com.jetbrains.python.parsing.console.PyConsoleParser
import com.jetbrains.python.parsing.console.PythonConsoleData
import com.jetbrains.python.parsing.console.PythonConsoleLexer
import com.jetbrains.python.psi.LanguageLevel

/**
 * Test that Python code parsing in the console works the same way as in files.
 */
class PythonInConsoleParsingTest : PythonParsingTest(PyConsoleParsingDefinition(true))

internal class PyConsoleParsingDefinition(private val isIPython: Boolean) : PythonParserDefinition() {
  override fun createLexer(project: Project) = PythonConsoleLexer()
  override fun createParser(project: Project) = PyConsoleParser(
    PythonConsoleData().apply { isIPythonEnabled = isIPython },
    LanguageLevel.getDefault()
  )
}
