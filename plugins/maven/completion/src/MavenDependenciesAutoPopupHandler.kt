// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.maven.completion.contributor.MavenCoordinateCompletionContributor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import org.jetbrains.idea.maven.dom.MavenDomUtil

internal class MavenDependenciesAutoPopupHandler : TypedHandlerDelegate() {
  override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (file !is XmlFile) return Result.CONTINUE
    if (!MavenDomUtil.isProjectFile(file)) return Result.CONTINUE
    if (!charTyped.isLetterOrDigit() && charTyped != '-' && charTyped != '.' && charTyped != ':') return Result.CONTINUE

    val offset = editor.caretModel.offset
    AutoPopupController.getInstance(project).scheduleAutoPopup(editor) { psiFile ->
      val element = psiFile.findElementAt(offset) ?: return@scheduleAutoPopup false
      val xmlText = element.parent as? XmlText ?: return@scheduleAutoPopup false
      val parentTag = xmlText.parent as? XmlTag ?: return@scheduleAutoPopup false
      val tagName = parentTag.name
      if (tagName != "dependencies" && tagName != "dependency") return@scheduleAutoPopup false
      MavenCoordinateCompletionContributor.trimDummy(xmlText.value).length >= 3
    }

    return Result.CONTINUE
  }
}
