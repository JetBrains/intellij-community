// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.idea.TestFor
import com.intellij.python.junit5Tests.framework.env.pyMockInterpreterFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.sdk.pyProjectInterpretersIfReady
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.milliseconds

/**
 * [pyProjectInterpretersIfReady] gives the interpreters that a [PyProject] owns, or the interpreters of every [PyProject]
 * for `null`.
 */
@TestApplication
internal class PyProjectInterpretersIfReadyTest {
  private val projectFixture = projectFixture()
  private val firstFixture = projectFixture.pyProjectFixture(projectFixture.pathInProjectFixture(Path("first")))
  private val secondFixture = projectFixture.pyProjectFixture(projectFixture.pathInProjectFixture(Path("second")))
  private val firstInterpreterFixture = firstFixture.pyMockInterpreterFixture(tempPathFixture())
  private val secondInterpreterFixture = secondFixture.pyMockInterpreterFixture(tempPathFixture())

  @Test
  @TestFor(issues = ["PY-92937"])
  fun `a PyProject gets its own interpreters, and null gets all of them`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val first = firstFixture.get()
    val second = secondFixture.get()
    val firstInterpreter = firstInterpreterFixture.get()
    val secondInterpreter = secondInterpreterFixture.get()
    // The snapshot of the Python projects follows the modules asynchronously.
    while (!pyProjectInterpretersIfReady(project, null).containsAll(listOf(firstInterpreter, secondInterpreter))) {
      delay(50.milliseconds)
    }

    assertThat(pyProjectInterpretersIfReady(project, null)).containsExactlyInAnyOrder(firstInterpreter, secondInterpreter)
    assertThat(pyProjectInterpretersIfReady(project, first)).containsExactly(firstInterpreter)
    assertThat(pyProjectInterpretersIfReady(project, second)).containsExactly(secondInterpreter)
  }
}
