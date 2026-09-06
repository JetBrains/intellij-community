// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typeInformation

import com.intellij.idea.TestFor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.PythonMockSdk
import com.jetbrains.python.PythonTestUtil
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.junit5.framework.pyMockSdkFixture
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkType
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
@TestFor(classes = [PyGenerateTypeInformationAction::class, PyTypeInformationGenerator::class])
@Subsystems.CodeInsight
@Layers.Functional
internal class PyTypeInformationGeneratorTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val moduleFixture = projectFixture.moduleFixture(tempPathFixture(), addPathToSourceRoot = true)
  private val sdkFixture = projectFixture.pyMockSdkFixture(moduleFixture) {
    PythonMockSdk.create(
      "typeInformationTestSdk",
      PythonTestUtil.getTestDataPath() + "/MockSdk",
      PythonSdkType.getInstance(),
      LanguageLevel.getLatest(),
    )
  }

  @Test
  fun `selects the first applicable generator`(): Unit = timeoutRunBlocking {
    val (project, sdk) = testContext()
    val selected = findApplicableTypeInformationGenerator(
      listOf(
        TestGenerator("first", applicable = false),
        TestGenerator("second", applicable = true),
        TestGenerator("third", applicable = true),
      ),
    ) { it.isApplicable(project, sdk) }

    assertEquals("second", selected?.presentableName)
  }

  @Test
  fun `skips a generator whose applicability check fails`(): Unit = timeoutRunBlocking {
    val (project, sdk) = testContext()
    val selected = findApplicableTypeInformationGenerator(
      listOf(
        TestGenerator("broken", failure = IllegalStateException("broken")),
        TestGenerator("usable", applicable = true),
      ),
    ) { it.isApplicable(project, sdk) }

    assertEquals("usable", selected?.presentableName)
  }

  @Test
  fun `returns null when no generator applies`(): Unit = timeoutRunBlocking {
    val (project, sdk) = testContext()
    val selected = findApplicableTypeInformationGenerator(
      listOf(TestGenerator("first", applicable = false)),
    ) { it.isApplicable(project, sdk) }

    assertNull(selected)
  }

  @Test
  fun `propagates cancellation from applicability check`(): Unit = timeoutRunBlocking {
    val (project, sdk) = testContext()
    val cancelled = try {
      findApplicableTypeInformationGenerator(
        listOf(TestGenerator("cancelled", failure = CancellationException("cancelled"))),
      ) { it.isApplicable(project, sdk) }
      false
    }
    catch (_: CancellationException) {
      true
    }

    assertTrue(cancelled)
  }

  private suspend fun testContext(): Pair<Project, Sdk> = projectFixture.get() to sdkFixture.get()

  private class TestGenerator(
    override val presentableName: String,
    private val applicable: Boolean = false,
    private val failure: Throwable? = null,
  ) : PyTypeInformationGenerator {
    override suspend fun isApplicable(project: Project, sdk: Sdk): Boolean {
      failure?.let { throw it }
      return applicable
    }

    override suspend fun generate(project: Project, sdk: Sdk): PyTypeInformationGenerationResult =
      PyTypeInformationGenerationResult.Success
  }
}
