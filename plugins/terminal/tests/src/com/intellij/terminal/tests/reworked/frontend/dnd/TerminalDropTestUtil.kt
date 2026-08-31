// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.frontend.dnd

import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.TransferableWrapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.psi.PsiElement
import com.intellij.testFramework.replaceService
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.nio.file.Path
import javax.swing.tree.TreeNode

internal fun virtualFileOf(path: Path): VirtualFile =
  requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)) { "No VirtualFile for $path" }

/** Builds the payload of a drag that started inside the IDE, for example in the Project View. */
internal fun ideDragPayload(files: List<VirtualFile>): TransferableWrapper = object : TransferableWrapper {
  override fun getTreeNodes(): Array<TreeNode>? = null
  override fun getPsiElements(): Array<PsiElement>? = null
  override fun getVirtualFiles(): Array<VirtualFile> = files.toTypedArray()
  override fun asFileList(): List<File>? = null
}

/** Builds a drop of [files] that started inside the IDE, with no drop point. */
internal fun ideDragOf(files: List<VirtualFile>): DnDEvent =
  mock { on { attachedObject } doReturn ideDragPayload(files) }

/** Builds a drop that carries no payload at all. */
internal fun emptyDrag(): DnDEvent = mock { on { attachedObject } doReturn null }

/** Builds the payload of a text drag that came from another application. */
internal fun nativeTextDrag(text: String): DnDEvent {
  val info = DnDNativeTarget.EventInfo(arrayOf(DataFlavor.stringFlavor), StringSelection(text))
  return mock { on { attachedObject } doReturn info }
}

/** Builds the payload of a file drag that came from another application, for example a file manager. */
internal fun nativeFileDrag(files: List<File>): DnDEvent {
  val transferable = object : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
      if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
      return files
    }
  }
  val info = DnDNativeTarget.EventInfo(arrayOf(DataFlavor.javaFileListFlavor), transferable)
  return mock { on { attachedObject } doReturn info }
}

/**
 * Replaces the product mode for one test.
 *
 * [IdeProductMode] reads the application service on every call, so the replacement applies at once.
 */
internal fun setProductMode(mode: ProductMode, disposable: Disposable) {
  val productMode = object : IdeProductMode {
    override val currentMode: ProductMode = mode
  }
  ApplicationManager.getApplication().replaceService(IdeProductMode::class.java, productMode, disposable)
}
