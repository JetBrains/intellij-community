// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.settings

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.exists

@TestApplication
class McpServerSettingsConfigurableTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val project by projectFixture

  private val projectRoot: Path
    get() = Path.of(checkNotNull(project.basePath))

  @AfterEach
  fun tearDown(): Unit = runBlocking(Dispatchers.EDT) {
    FileEditorManagerEx.getInstanceEx(project).closeAllFiles()
  }

  @Test
  fun `openFileInEditor opens existing file`(): Unit = runBlocking(Dispatchers.EDT) {
    val existingPath = projectRoot.resolve(".mcp/settings.json")
    val existingFile = runWriteAction {
      VfsUtil.createDirectories(existingPath.parent.toString()).findOrCreateFile(existingPath.fileName.toString())
    }

    openFileInEditor(existingPath, project)

    assertThat(FileEditorManager.getInstance(project).isFileOpen(existingFile)).isTrue()
  }

  @Test
  fun `openFileInEditor creates missing file and opens it`(): Unit = runBlocking(Dispatchers.EDT) {
    val missingPath = projectRoot.resolve(".codex/config.toml")
    assertThat(missingPath.exists()).isFalse()

    openFileInEditor(missingPath, project)

    val createdFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(missingPath)
    assertThat(missingPath.exists()).isTrue()
    assertThat(createdFile).isNotNull()
    assertThat(FileEditorManager.getInstance(project).isFileOpen(createdFile!!)).isTrue()
  }
}
