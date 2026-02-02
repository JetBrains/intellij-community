// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.inlay

import com.intellij.codeInsight.hints.declarative.EndOfLinePosition
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.OwnBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.project.MavenProjectsManager

class MavenModelVersionInlayHintProvider : InlayHintsProvider {
  override fun createCollector(file: PsiFile, editor: Editor): MavenModelVersionInlayHintCollector = MavenModelVersionInlayHintCollector(editor)

  override fun isDumbAware(): Boolean = true
}

class MavenModelVersionInlayHintCollector(val editor: Editor) : OwnBypassCollector {
  override fun collectHintsForFile(file: PsiFile, sink: InlayTreeSink) {
    if (file !is XmlFile) return
    val manager = MavenProjectsManager.getInstance(file.project)
    val vFile = file.virtualFile
    if (manager.findProject(vFile) == null) return
    val modelTag = file.rootTag?.findFirstSubTag("modelVersion") ?: return
    val line = editor.document.getLineNumber(modelTag.textOffset)
    val modelVersion = MavenDomUtil.getXmlProjectModelVersion(file) ?: return
    if (modelVersion == MavenConstants.MODEL_VERSION_4_0_0) {
      sink.addPresentation(EndOfLinePosition(line), hintFormat = HintFormat.default) {
        text(MavenDomBundle.message("maven.version.inlay.hint.text.3"))
      }
    }
    else if (modelVersion == MavenConstants.MODEL_VERSION_4_1_0) {
      sink.addPresentation(EndOfLinePosition(line), hintFormat = HintFormat.default) {
        text(MavenDomBundle.message("maven.version.inlay.hint.text.4"))
      }
    }
  }

}