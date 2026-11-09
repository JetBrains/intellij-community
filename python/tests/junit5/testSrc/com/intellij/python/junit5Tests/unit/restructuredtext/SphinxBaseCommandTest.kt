// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.restructuredtext

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.python.junit5Tests.framework.errorSinkFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.restructuredtext.python.PythonRestBundle
import com.intellij.restructuredtext.python.SphinxBaseCommand
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.MessageError
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@TestFor(issues = ["PY-93936"])
@Subsystems.Run
@Layers.Functional
@TestApplication
internal class SphinxBaseCommandTest {
  private val projectFixture = projectFixture()
  private val pyProjectFixture = projectFixture.pyProjectFixture()
  private val errorSinkFixture = errorSinkFixture()

  @Test
  fun `the quickstart command reports a Python project without an interpreter`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val pyProject = pyProjectFixture.get()
    val errorSink = errorSinkFixture.get()
    ApplicationManager.getApplication().replaceService(ErrorSink::class.java, errorSink, disposable)
    val main = EvoPyProjectModel.getInstance(project).snapshotFlow().first { it.main != null }.main!!
    assertThat(main.pyProject).isEqualTo(pyProject)
    assertThat(main.interpreter).isNull()

    SphinxBaseCommand().execute(project, null)

    while (errorSink.errors.isEmpty()) {
      delay(10.milliseconds)
    }
    val detail = errorSink.errors.single()
    assertThat(detail.project).isEqualTo(project)
    assertThat(detail.error).isInstanceOf(MessageError::class.java)
    assertThat(detail.error.message).isEqualTo(PythonRestBundle.message("python.rest.no.sdk.specified"))
    errorSink.clear()
  }
}
