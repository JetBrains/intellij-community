// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.model

import com.intellij.openapi.application.edtWriteAction
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.vfs.createDirectory
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Tests how characters in `[project].name` from `pyproject.toml` flow through
 * to the resulting JPS module name and on-disk `.iml` filename.
 *
 * Complements [PyProjectTomlNamingTest], which covers duplicate resolution and rename flows.
 */
@TestApplication
internal class PyProjectTomlNameCharactersTest {

  private val tempDirFixture = tempPathFixture()
  private val projectFixture = projectFixture(pathFixture = tempDirFixture)
  private val f by pyProjectTomlSyncFixture(projectFixture, tempDirFixture)

  @Test
  fun `plain ascii name flows through unchanged`(): Unit = timeoutRunBlocking(30.seconds) {
    edtWriteAction {
      f.root.writePyprojectToml("root")
      f.root.createDirectory("sub").writePyprojectToml("simple_pkg")
    }

    f.reloadProject()

    f.assertProjectStructure(
      ExpectedModule("root", contentRoot = "."),
      ExpectedModule("simple_pkg", contentRoot = "sub"),
    )
  }

  @Test
  fun `dotted name becomes module name with dotted iml`(): Unit = timeoutRunBlocking(30.seconds) {
    edtWriteAction {
      f.root.writePyprojectToml("root")
      f.root.createDirectory("sub").writePyprojectToml("my.namespace.pkg")
    }

    f.reloadProject()

    f.assertProjectStructure(
      ExpectedModule("root", contentRoot = "."),
      ExpectedModule("my.namespace.pkg", contentRoot = "sub"),
    )
  }

  @Test
  fun `dash and underscore variants are distinct sibling modules`(): Unit = timeoutRunBlocking(30.seconds) {
    edtWriteAction {
      f.root.writePyprojectToml("root")
      f.root.createDirectory("dash").writePyprojectToml("my-pkg")
      f.root.createDirectory("under").writePyprojectToml("my_pkg")
    }

    f.reloadProject()

    f.assertProjectStructure(
      ExpectedModule("root", contentRoot = "."),
      ExpectedModule("my-pkg", contentRoot = "dash"),
      ExpectedModule("my_pkg", contentRoot = "under"),
    )
  }

  @Test
  fun `case-different names are distinct sibling modules`(): Unit = timeoutRunBlocking(30.seconds) {
    edtWriteAction {
      f.root.writePyprojectToml("root")
      f.root.createDirectory("a").writePyprojectToml("Friendly-Bard")
      f.root.createDirectory("b").writePyprojectToml("friendly-bard")
    }

    f.reloadProject()

    f.assertProjectStructure(
      ExpectedModule("root", contentRoot = "."),
      ExpectedModule("Friendly-Bard", contentRoot = "a"),
      ExpectedModule("friendly-bard", contentRoot = "b"),
    )
  }

  @Test
  fun `unicode name is preserved on disk`(): Unit = timeoutRunBlocking(30.seconds) {
    edtWriteAction {
      f.root.writePyprojectToml("root")
      f.root.createDirectory("sub").writePyprojectToml("пакет")
    }

    f.reloadProject()

    f.assertProjectStructure(
      ExpectedModule("root", contentRoot = "."),
      ExpectedModule("пакет", contentRoot = "sub"),
    )
  }

}
