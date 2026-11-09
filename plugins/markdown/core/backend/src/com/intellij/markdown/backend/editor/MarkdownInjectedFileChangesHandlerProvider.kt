// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.editor

import com.intellij.injected.editor.InjectedFileChangesHandler
import com.intellij.injected.editor.InjectedFileChangesHandlerProvider
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost.Shred
import com.intellij.psi.impl.source.tree.injected.changesHandler.CommonInjectedFileChangesHandler

internal class MarkdownInjectedFileChangesHandlerProvider : InjectedFileChangesHandlerProvider {
  override fun createFileChangesHandler(
    shreds: List<Shred>,
    hostEditor: Editor,
    newDocument: Document,
    injectedFile: PsiFile,
  ): InjectedFileChangesHandler = MarkdownInjectedFileChangesHandler(shreds, hostEditor, newDocument, injectedFile)
}

private class MarkdownInjectedFileChangesHandler(
  shreds: List<Shred>,
  hostEditor: Editor,
  fragmentDocument: Document,
  injectedFile: PsiFile,
) : CommonInjectedFileChangesHandler(shreds, hostEditor, fragmentDocument, injectedFile) {
  override fun commitToOriginal(e: DocumentEvent) {
    super.commitToOriginal(e)
    val host = markers.firstNotNullOfOrNull { it.host } ?: return
    rebuildMarkers(host.textRange)
  }
}
