// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.util.xml.DomFileElement
import com.intellij.util.xml.highlighting.BasicDomElementsInspection
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import org.jetbrains.idea.maven.buildtool.quickfix.AddModelVersionQuickFix
import org.jetbrains.idea.maven.buildtool.quickfix.UpdateXmlsTo410
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.MavenDomUtil.isAtLeastMaven4
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel

class MavenModelVersionMissedInspection : BasicDomElementsInspection<MavenDomProjectModel?>(MavenDomProjectModel::class.java) {
  override fun getGroupDisplayName(): String {
    return MavenDomBundle.message("inspection.group")
  }

  override fun getDefaultLevel(): HighlightDisplayLevel {
    return HighlightDisplayLevel.ERROR
  }

  override fun checkFileElement(
    domFileElement: DomFileElement<MavenDomProjectModel?>,
    holder: DomElementAnnotationHolder,
  ) {
    val projectModel = domFileElement.getRootElement()
    if (projectModel.modelVersion.exists()) return
    if (isAtLeastMaven4(domFileElement.file.virtualFile, domFileElement.file.project)) {
      if (projectModel.effectiveModelVersion != null) return
      holder.createProblem(
        projectModel,
        HighlightSeverity.ERROR,
        MavenDomBundle.message("inspection.missed.model.version"),
        AddModelVersionQuickFix(),
        UpdateXmlsTo410(),
      )
    }
    else {
      holder.createProblem(
        projectModel,
        HighlightSeverity.ERROR,
        MavenDomBundle.message("inspection.missed.model.version"),
        AddModelVersionQuickFix(),
      )
    }
  }
}
