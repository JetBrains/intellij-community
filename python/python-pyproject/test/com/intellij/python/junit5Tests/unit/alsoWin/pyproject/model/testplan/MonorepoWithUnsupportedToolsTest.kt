// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.testplan

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.modules
import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.div
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.ExpectedModule
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.PYTHON
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.PyProjectTomlSyncTestFixture
import com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model.pyProjectTomlSyncFixture
import com.intellij.python.pyproject.model.internal.SuggestedSdk
import com.intellij.python.pyproject.model.internal.suggestSdk
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/monorepo/monorepo_with_unsupported_tools")
internal class MonorepoWithUnsupportedToolsTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val f by pyProjectTomlSyncFixture(projectFixture)

  @Test
  fun sanity(): Unit = timeoutRunBlocking {
    f.reloadProject()
    f.assertProjectStructure(
      ExpectedModule(f.implicitModuleName, type = PYTHON, contentRoot = ".", sourceRoots = listOf(".")),
      ExpectedModule("flit1_name", contentRoot = "flit" / "flit1"),
      ExpectedModule("name2", contentRoot = "flit" / "flit2"),
      ExpectedModule("hatch1", contentRoot = "hatch" / "hatch1", sourceRoots = listOf("hatch" / "hatch1" / "src")),
      ExpectedModule("hatch2", contentRoot = "hatch" / "hatch2", sourceRoots = listOf("hatch" / "hatch2" / "src")),
      ExpectedModule("pdm1", contentRoot = "pdm" / "pdm1" / "pdm1", sourceRoots = listOf("pdm" / "pdm1" / "pdm1" / "src")),
      ExpectedModule("pdm2", contentRoot = "pdm" / "pdm2" / "pdm2"),
      ExpectedModule("poetry1", contentRoot = "poetry" / "poetry1", sourceRoots = listOf("poetry" / "poetry1" / "src")),
      ExpectedModule("poetry2", contentRoot = "poetry" / "poetry2", sourceRoots = listOf("poetry" / "poetry2" / "src")),
      ExpectedModule("uv", contentRoot = "uv"),
      ExpectedModule("subuv1", contentRoot = "uv" / "subuv1"),
      ExpectedModule("subuv2", contentRoot = "uv" / "subuv2"),
    )
    assertUvWorkspaceRootIsUv()
  }

  /**
   * `uv/` declares the workspace, so `uv/` is the directory every uv command runs in.
   *
   * The monorepo root holds no `pyproject.toml`. It is only a container. PY-92193 reports the environment at that
   * root, so the test states where the workspace really is.
   *
   * The assertion belongs to [sanity] because the sample is copied into one class-level project. A second `@Test`
   * would run the fixture setup again over the same directory.
   */
  private suspend fun assertUvWorkspaceRootIsUv() {
    val uv = f.moduleNamed("uv")
    for (memberName in listOf("subuv1", "subuv2")) {
      when (val suggested = f.moduleNamed(memberName).suggestSdk()) {
        is SuggestedSdk.SameAs -> {
          assertThat(suggested.parentModule)
            .describedAs("'%s' is a member of the workspace that 'uv' declares", memberName)
            .isEqualTo(uv)
          assertThat(suggested.accordingTo.id).isEqualTo("uv")
        }
        is SuggestedSdk.PyProjectIndependent, null ->
          error("'$memberName' must be a uv workspace member, got $suggested")
      }
    }
    assertThat(uv.suggestSdk())
      .describedAs("the workspace root points at no other module")
      .isInstanceOf(SuggestedSdk.PyProjectIndependent::class.java)
  }

  private fun PyProjectTomlSyncTestFixture.moduleNamed(name: String): Module = project.modules.single { it.name == name }
}
