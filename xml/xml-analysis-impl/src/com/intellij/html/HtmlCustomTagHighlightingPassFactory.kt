// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.html

import com.intellij.codeHighlighting.*
import com.intellij.codeInsight.daemon.impl.tagTreeHighlighting.XmlTagTreeHighlightingUtil
import com.intellij.lang.html.HtmlCompatibleFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.html.HtmlFileImpl
import com.intellij.psi.xml.XmlFile
import com.intellij.xml.util.HtmlUtil

class HtmlCustomTagHighlightingPassFactory : TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {

  override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
    registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
  }

  override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
    if (!hasHtmlViewProvider(file) && !HtmlUtil.supportsXmlTypedHandlers(file)) {
      return null
    }
    return HtmlCustomTagHighlightingPass(file, editor)
  }

  private fun hasHtmlViewProvider(file: PsiFile): Boolean {
    return file.viewProvider.allFiles.any { it is HtmlCompatibleFile }
  }
}