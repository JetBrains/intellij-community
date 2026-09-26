// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.markdown.preview

import com.intellij.ide.BrowserUtil
import com.intellij.mermaid.MermaidBundle
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.jcef.JBCefApp
import com.intellij.util.application
import java.util.function.Function
import javax.swing.JComponent

// TODO: JCefImageViewer became the default image viewer in 232, so this should be removed after dropping 232 support
internal class ExportedDiagramEditorNotificationProvider: EditorNotificationProvider {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (!Registry.`is`("mermaid.export.diagram.action.enable", false)) {
      return null
    }
    if (!wasGeneratedByPlugin(file)) {
      return null
    }
    return Function { editor -> createNotificationComponent(project, editor, file) }
  }

  private fun createNotificationComponent(project: Project, editor: FileEditor, file: VirtualFile): JComponent? {
    if (isAlreadyBrowserBasedSvgViewer(editor)) {
      return null
    }
    val panel = EditorNotificationPanel(
      editor,
      EditorNotificationPanel.Status.Warning
    )
    panel.text = MermaidBundle.message("exported.diagram.editor.notification.text")
    panel.createActionLabel(MermaidBundle.message("exported.diagram.editor.notification.open.in.browser.action.text")) {
      BrowserUtil.browse(file)
    }
    if (canUseBrowserBasedSvgViewer() && !isBrowserBasedSvgViewerEnabled()) {
      panel.createActionLabel(MermaidBundle.message("exported.diagram.editor.notification.enable.browser.svg.viewer.action.text")) {
        application.assertIsDispatchThread()
        enableBrowserBasedSvgViewer()
        FileEditorManager.getInstance(project).closeFile(file)
        FileEditorManager.getInstance(project).openFile(file)
      }
    }
    return panel
  }

  private fun wasGeneratedByPlugin(file: VirtualFile): Boolean {
    // Use reflection here, so we won't have to deal with a dependency on com.intellij.platform.images module
    if (file.fileType::class.java.name != "org.intellij.images.fileTypes.impl.SvgFileType") {
      return false
    }
    val attributeName = "data-ij-mermaid-generated-on-export"
    return file.inputStream.reader().useLines { lines ->
      return@useLines lines.any { it.contains(attributeName) }
    }
  }

  private fun isAlreadyBrowserBasedSvgViewer(editor: FileEditor): Boolean {
    val editorWithPreview = editor as? TextEditorWithPreview ?: return false
    val preview = editorWithPreview.previewEditor
    return preview::class.java.name == "org.intellij.images.editor.impl.jcef.JCefImageViewer"
  }

  private fun canUseBrowserBasedSvgViewer(): Boolean {
    val result = runCatching {
      if (!JBCefApp.isSupported()) {
        return@runCatching false
      }
      // Will throw if there is no such key for some reason
      Registry.`is`("ide.browser.jcef.svg-viewer.enabled")
      return@runCatching true
    }
    return result.getOrDefault(false)
  }

  private fun isBrowserBasedSvgViewerEnabled(): Boolean {
    val result = runCatching { Registry.`is`("ide.browser.jcef.svg-viewer.enabled", false) }
    return result.getOrDefault(false)
  }

  private fun enableBrowserBasedSvgViewer() {
    Registry.get("ide.browser.jcef.svg-viewer.enabled").setValue(true)
  }
}
