// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.inspections

import com.intellij.psi.xml.XmlFile
import com.intellij.util.xml.DomFileElement
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.converters.MavenConsumerPomUtil.isAutomaticVersionFeatureEnabled
import org.jetbrains.idea.maven.dom.model.MavenDomParent
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel

class MavenParentMissedVersionInspection : MavenParentMissedCoordinatesInspection() {
  override fun checkFileElement(domFileElement: DomFileElement<MavenDomProjectModel?>,
                                holder: DomElementAnnotationHolder) {
    val parent = getParentIfExists(domFileElement) ?: return
    val pom = domFileElement.file
    if (!parent.version.exists() && !isAutomaticVersionFeatureEnabled(pom.virtualFile, pom.project)) {
      val version = getParentVersion(pom, parent)
      reportMissedChildTagProblem(holder, parent, "version", version)
    }
  }

  private fun getParentVersion(currentFile: XmlFile, parentElement: MavenDomParent): String {
    // TODO probably rely on <relativePath> instead of just taking a pom from an upper directory
    val xmlFileParent = currentFile.parent?.parent?.findFile("pom.xml") as? XmlFile ?: return ""
    val parentModel = MavenDomUtil.getMavenDomModel(xmlFileParent, MavenDomProjectModel::class.java) ?: return ""
    if (parentModel.artifactId.value == parentElement.artifactId.value && parentModel.groupId.value == parentElement.groupId.value) {
      return parentModel.version.value ?: ""
    }
    return ""
  }

  override fun getShortName(): String {
    return "MavenParentMissedVersionInspection"
  }
}