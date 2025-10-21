// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.util.xml.DomFileElement
import com.intellij.util.xml.highlighting.BasicDomElementsInspection
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import org.jetbrains.idea.maven.buildtool.quickfix.UpdateXmlsTo410
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.converters.MavenPackagingConverter.Companion.MAVEN_4_SPECIFIC
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.model.MavenConstants.MODEL_VERSION_4_1_0

class MavenBomPackagingInOldSchema : BasicDomElementsInspection<MavenDomProjectModel?>(MavenDomProjectModel::class.java) {
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

    if (!projectModel.modelVersion.exists() || projectModel.modelVersion.stringValue == MODEL_VERSION_4_1_0) return
    val packaging = if (projectModel.packaging.exists()) projectModel.packaging.stringValue else return
    if (packaging == null || projectModel.packaging.stringValue !in MAVEN_4_SPECIFIC) return

    holder.createProblem(projectModel.modelVersion,
                         HighlightSeverity.ERROR,
                         MavenDomBundle.message("inspection.new.packaging.in.old.model", packaging),
                         UpdateXmlsTo410()
    )
    holder.createProblem(projectModel.packaging,
                         HighlightSeverity.ERROR,
                         MavenDomBundle.message("inspection.new.packaging.in.old.model", packaging),
                         UpdateXmlsTo410()
    )
  }
}