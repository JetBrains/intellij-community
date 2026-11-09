// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.idea.TestFor
import com.intellij.openapi.application.readAction
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestFor(classes = [PyDebuggerEditorsProvider::class], issues = ["PY-93903"])
@TestApplication
@Subsystems.Debugger
@Layers.Functional
internal class PyDebuggerEditorsProviderTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @Test
  fun `createExpression outside a read action keeps the text and the hidden imports`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val provider = PyDebuggerEditorsProvider()
    val source = XExpressionImpl("path.join('a', 'b')", PythonLanguage.INSTANCE, "from os import path", EvaluationMode.EXPRESSION)
    val document = readAction { provider.createDocument(project, source, null, EvaluationMode.EXPRESSION) }

    val expression = withContext(Dispatchers.Default) {
      provider.createExpression(project, document, PythonLanguage.INSTANCE, EvaluationMode.EXPRESSION)
    }

    assertThat(expression.expression).isEqualTo("path.join('a', 'b')")
    assertThat(expression.customInfo).isEqualTo("from os import path")
  }
}
