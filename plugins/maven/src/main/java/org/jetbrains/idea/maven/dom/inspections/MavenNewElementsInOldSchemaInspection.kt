// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.util.xml.DomFileElement
import com.intellij.util.xml.highlighting.BasicDomElementsInspection
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import org.jetbrains.idea.maven.buildtool.quickfix.UpdateXmlsTo410
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel

class MavenNewElementsInOldSchemaInspection : BasicDomElementsInspection<MavenDomProjectModel?>(MavenDomProjectModel::class.java) {
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
    if (projectModel.modelVersion.value == "4.1.0") return
    if (projectModel.subprojects.exists()) {
      holder.createProblem(projectModel.subprojects,
                           HighlightSeverity.ERROR,
                           MavenDomBundle.message("inspection.wrong.model.version"),
                           UpdateXmlsTo410())
    }
  }
}