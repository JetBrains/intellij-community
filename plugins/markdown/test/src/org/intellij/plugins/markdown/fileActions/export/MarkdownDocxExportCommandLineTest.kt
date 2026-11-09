// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org.intellij.plugins.markdown.fileActions.export

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class MarkdownDocxExportCommandLineTest {
  private val projectFixture = projectFixture(
    openProjectTask = createTestOpenProjectOptions(runPostStartUpActivities = false),
    openAfterCreation = true,
  )
  private val sourceRoot = projectFixture.moduleFixture("src").sourceRootFixture()
  private val psiFile = sourceRoot.psiFileFixture("README.md", "![](logo.jpeg)\n")

  private val mdFile: VirtualFile
    get() = psiFile.get().virtualFile

  @Test
  fun `pandoc runs in the directory of the Markdown file`() {
    val commandLine = createMdToDocxCommandLine("pandoc", mdFile, "/out/README.docx", "/missing/README.docx")

    assertThat(commandLine.workDirectory?.toPath()).isEqualTo(mdFile.parent.toNioPath())
    assertThat(commandLine.parametersList.list).containsExactly(mdFile.path, "-f", "markdown", "-t", "docx", "-o", "/out/README.docx")
  }

  @Test
  fun `an existing reference document is passed to pandoc`() {
    val refFile = mdFile.parent.toNioPath().resolve("README.docx").toFile().apply { writeText("") }

    val commandLine = createMdToDocxCommandLine("pandoc", mdFile, "/out/README.docx", refFile.path)

    assertThat(commandLine.parametersList.list).endsWith("--reference-doc=${refFile.path}")
  }
}
