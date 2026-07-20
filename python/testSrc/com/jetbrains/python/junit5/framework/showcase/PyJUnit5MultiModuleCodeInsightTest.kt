// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.showcase

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.python.junit5Tests.framework.metaInfo.Repository
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.moduleInProjectFixture
import com.jetbrains.python.junit5.framework.annotations.PyCodeInsightTestApplication
import com.jetbrains.python.junit5.framework.pyExternalSystemProjectFixture
import com.jetbrains.python.junit5.framework.util.completeBasicAtProjectFile
import com.jetbrains.python.testDataPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.time.Duration.Companion.minutes

/**
 * Showcase for completion across modules of a reloaded uv workspace.
 *
 * [PyCodeInsightTestApplication] normally creates an empty temporary project. Explicit fixtures replace it with
 * the workspace project and primary module, while the annotation still supplies the mock SDK and [CodeInsightTestFixture].
 */
@TestClassInfo(Repository.PY_COMMUNITY)
@TestDataPath($$"$CONTENT_ROOT/../testData/junit5/showcase/multimodule")
@PyCodeInsightTestApplication
internal class PyJUnit5MultiModuleCodeInsightTest(val project: Project) {

  companion object {
    private val MULTI_MODULE_TEST_DATA =
      Path.of(testDataPath) / "junit5/showcase/multimodule"

    @JvmField
    val projectFixture = pyExternalSystemProjectFixture(MULTI_MODULE_TEST_DATA)

    @JvmField
    val moduleFixture = projectFixture.moduleInProjectFixture("analyzer")
  }

  @Test
  fun `completion resolves symbols across uv workspace modules`(fixture: CodeInsightTestFixture): Unit =
    timeoutRunBlocking(1.minutes) {
      assertThat(project.modules.size).isGreaterThanOrEqualTo(5)
      assertThat(fixture.completeBasicAtProjectFile("tools/analyzer/src/analyzer/_complete.py", "analyzer"))
        .contains("mean", "median")
    }

  @Test
  fun `completion survives fixture cleanup between tests`(fixture: CodeInsightTestFixture): Unit =
    timeoutRunBlocking(1.minutes) {
      assertThat(project.modules.size).isGreaterThanOrEqualTo(5)
      assertThat(fixture.completeBasicAtProjectFile("tools/reporter/src/reporter/cli.py", "reporter"))
        .contains("CsvFormatter", "TableBuilder")
    }
}
