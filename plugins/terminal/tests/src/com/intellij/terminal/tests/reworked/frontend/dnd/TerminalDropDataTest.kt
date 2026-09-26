// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.dnd

import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.TransferableWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.terminal.frontend.dnd.TerminalDropData
import com.intellij.testFramework.LightVirtualFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.io.IOException
import javax.swing.tree.TreeNode

/**
 * Tests how [TerminalDropData] reads a drop payload.
 */
internal class TerminalDropDataTest {

  // ---------- an IDE drag, which carries a TransferableWrapper ----------

  @Test
  fun `an IDE drag with two virtual files reports both files and no path and no text`() {
    val files = listOf(virtualFile("a.txt"), virtualFile("b.txt"))

    val data = TerminalDropData(dropEventWith(ideDrag(virtualFiles = files)))

    assertThat(data.virtualFiles).containsExactlyElementsOf(files)
    assertThat(data.paths).isEmpty()
    assertThat(data.text).isNull()
  }

  @Test
  fun `an IDE drag whose virtual files are null falls through to the file list`() {
    val file = File("/tmp/dropped.txt")

    val data = TerminalDropData(dropEventWith(ideDrag(virtualFiles = null, fileList = listOf(file))))

    assertThat(data.virtualFiles).isEmpty()
    assertThat(data.paths).containsExactly(file.toPath())
    assertThat(data.text).isNull()
  }

  @Test
  fun `an IDE drag whose virtual files are empty falls through to the file list`() {
    val file = File("/tmp/dropped.txt")

    val data = TerminalDropData(dropEventWith(ideDrag(virtualFiles = emptyList(), fileList = listOf(file))))

    assertThat(data.virtualFiles).isEmpty()
    assertThat(data.paths).containsExactly(file.toPath())
    assertThat(data.text).isNull()
  }

  @Test
  fun `an IDE drag with neither files nor a file list reports nothing`() {
    // An IDE drag is not a DnDNativeTarget.EventInfo, so it can never supply the text either.
    val data = TerminalDropData(dropEventWith(ideDrag(virtualFiles = emptyList(), fileList = null)))

    assertThat(data.virtualFiles).isEmpty()
    assertThat(data.paths).isEmpty()
    assertThat(data.text).isNull()
  }

  // ---------- a native drag, which carries a DnDNativeTarget.EventInfo ----------

  @Test
  fun `a native drag with a java file list reports the paths and no text`() {
    val files = listOf(File("/tmp/one.txt"), File("/tmp/two.txt"))

    val data = TerminalDropData(dropEventWith(nativeDrag(DataFlavor.javaFileListFlavor to files)))

    assertThat(data.virtualFiles).isEmpty()
    assertThat(data.paths).containsExactlyElementsOf(files.map(File::toPath))
    assertThat(data.text).isNull()
  }

  @Test
  fun `a native drag with both a file list and a string prefers the file list`() {
    val file = File("/tmp/one.txt")

    val data = TerminalDropData(
      dropEventWith(
        nativeDrag(
          DataFlavor.stringFlavor to "some dragged text",
          DataFlavor.javaFileListFlavor to listOf(file),
        )
      )
    )

    assertThat(data.paths).containsExactly(file.toPath())
    assertThat(data.text).isNull()
  }

  @Test
  fun `a native drag with only a string reports that string as the text`() {
    val data = TerminalDropData(dropEventWith(nativeDrag(DataFlavor.stringFlavor to "echo hello")))

    assertThat(data.virtualFiles).isEmpty()
    assertThat(data.paths).isEmpty()
    assertThat(data.text).isEqualTo("echo hello")
  }

  @Test
  fun `a native drag with an empty string reports an empty text`() {
    // The caller drops a blank text, so the empty string must reach it as an empty string and not as null.
    val data = TerminalDropData(dropEventWith(nativeDrag(DataFlavor.stringFlavor to "")))

    assertThat(data.text).isEqualTo("")
  }

  @Test
  fun `a native drag with an unsupported flavor reports nothing`() {
    val data = TerminalDropData(dropEventWith(nativeDrag(DataFlavor.imageFlavor to "not a string")))

    assertThat(data.virtualFiles).isEmpty()
    assertThat(data.paths).isEmpty()
    assertThat(data.text).isNull()
  }

  @Test
  fun `a native drag whose string flavor fails reports no text`() {
    // A slow or broken drag source can fail the transfer. The failure must not escape the drop handler,
    // because the handler runs on the EDT inside a write-intent read action.
    val data = TerminalDropData(
      dropEventWith(nativeDrag(failOn = DataFlavor.stringFlavor, entries = arrayOf(DataFlavor.stringFlavor to "unused")))
    )

    assertThat(data.text).isNull()
  }

  @Test
  fun `a native drag whose string flavor returns a non-string reports no text`() {
    val data = TerminalDropData(dropEventWith(nativeDrag(DataFlavor.stringFlavor to 42)))

    assertThat(data.text).isNull()
  }

  // ---------- a drop that carries nothing usable ----------

  @Test
  fun `a drop without an attached object reports nothing`() {
    val data = TerminalDropData(dropEventWith(null))

    assertThat(data.virtualFiles).isEmpty()
    assertThat(data.paths).isEmpty()
    assertThat(data.text).isNull()
  }

  @Test
  fun `a drop with an unrelated attached object reports nothing`() {
    val data = TerminalDropData(dropEventWith("a bare string that is not a known payload"))

    assertThat(data.virtualFiles).isEmpty()
    assertThat(data.paths).isEmpty()
    assertThat(data.text).isNull()
  }
}

private fun virtualFile(name: String): VirtualFile = LightVirtualFile(name, "")

/** Stubs the only member of [DnDEvent] that [TerminalDropData] reads. */
private fun dropEventWith(attached: Any?): DnDEvent = mock {
  on { attachedObject } doReturn attached
}

/**
 * Builds the payload of a drag that started inside the IDE, for example in the Project View.
 *
 * [TerminalDropData] reads [TransferableWrapper.getVirtualFiles] first, then falls back to
 * [com.intellij.ide.dnd.FileFlavorProvider.asFileList].
 */
private fun ideDrag(virtualFiles: List<VirtualFile>?, fileList: List<File>? = null): TransferableWrapper =
  object : TransferableWrapper {
    override fun getTreeNodes(): Array<TreeNode>? = null
    override fun getPsiElements(): Array<PsiElement>? = null
    override fun getVirtualFiles(): Array<VirtualFile>? = virtualFiles?.toTypedArray()
    override fun asFileList(): List<File>? = fileList
  }

/** Builds the payload of a drag that came from another application, for example a file manager. */
private fun nativeDrag(vararg entries: Pair<DataFlavor, Any?>): DnDNativeTarget.EventInfo =
  nativeDrag(failOn = null, entries = entries)

private fun nativeDrag(failOn: DataFlavor?, entries: Array<out Pair<DataFlavor, Any?>>): DnDNativeTarget.EventInfo {
  val data = entries.toMap()
  return DnDNativeTarget.EventInfo(data.keys.toTypedArray(), FakeTransferable(data, failOn))
}

/** A [Transferable] that a test drives by flavor. Every unlisted flavor is unsupported. */
private class FakeTransferable(
  private val data: Map<DataFlavor, Any?>,
  private val failOn: DataFlavor?,
) : Transferable {
  override fun getTransferDataFlavors(): Array<DataFlavor> = data.keys.toTypedArray()

  override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = data.containsKey(flavor)

  override fun getTransferData(flavor: DataFlavor): Any? {
    if (flavor == failOn) throw IOException("simulated drag source failure")
    if (!data.containsKey(flavor)) throw UnsupportedFlavorException(flavor)
    return data[flavor]
  }
}
