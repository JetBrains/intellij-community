// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.internal.platformBridge.rebuildPyProjectModelForTest
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * The search reads a file and not a document, so a build writes an unsaved `pyproject.toml` to disk first
 * (PY-91841).
 *
 * A user edits `pyproject.toml` in an editor, and the model must read what the editor shows. The save runs
 * in a write action on a background thread, which this case covers. Every other test writes with `java.nio`
 * and holds no document, so the save returns at once for them.
 */
@TestApplication
internal class PySaveTomlDocumentTest {
  private val pathFixture = tempPathFixture()
  private val projectFixture = projectFixture(pathFixture)

  @Test
  fun testABuildWritesAnUnsavedTomlToDisk(): Unit = timeoutRunBlocking {
    val toml = pathFixture.get().resolve(PY_PROJECT_TOML)
    toml.writeText("[project]\nname = \"before\"\n")
    val file = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(toml)!!

    val fileDocumentManager = FileDocumentManager.getInstance()
    val document = readAction { fileDocumentManager.getDocument(file) }!!
    // A change of a document needs the EDT, because it runs through the command processor.
    edtWriteAction { document.setText("[project]\nname = \"after\"\n") }

    assertThat(fileDocumentManager.unsavedDocuments)
      .describedAs("the case needs an unsaved document, or it proves nothing")
      .contains(document)
    assertThat(toml.readText()).describedAs("the disk still holds the old text").contains("before")

    rebuildPyProjectModelForTest(projectFixture.get())

    assertThat(toml.readText()).describedAs("the build must write the document to disk").contains("after")
    assertThat(fileDocumentManager.unsavedDocuments)
      .describedAs("the document is saved now")
      .doesNotContain(document)
  }
}
