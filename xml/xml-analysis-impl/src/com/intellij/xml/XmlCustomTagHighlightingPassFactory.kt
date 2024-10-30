// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml

import com.intellij.codeHighlighting.*
import com.intellij.codeInsight.daemon.impl.tagTreeHighlighting.XmlTagTreeHighlightingUtil.hasXmlViewProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.intellij.xml.util.HtmlUtil

class XmlCustomTagHighlightingPassFactory : TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {

  override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
    registrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
  }

  override fun createHighlightingPass(psiFile: PsiFile, editor: Editor): TextEditorHighlightingPass? {
    if (!hasXmlViewProvider(psiFile) && !HtmlUtil.supportsXmlTypedHandlers(psiFile)) {
      return null
    }
    return XmlCustomTagHighlightingPass(psiFile, editor)
  }
}