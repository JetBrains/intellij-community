// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.inspections

import com.intellij.util.xml.DomFileElement
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel

class MavenParentMissedGroupIdArtefactIdInspection : MavenParentMissedCoordinatesInspection() {
  override fun checkFileElement(domFileElement: DomFileElement<MavenDomProjectModel?>,
                                holder: DomElementAnnotationHolder) {
    val parent = getParentIfExists(domFileElement) ?: return
    // the cases below are possible when tag does not exist and converters did not get values from parent pom
    if (!parent.groupId.exists() && parent.groupId.value == null) {
      reportMissedChildTagProblem(holder, parent, "groupId")
    }
    if (!parent.artifactId.exists() && parent.artifactId.value == null) {
      reportMissedChildTagProblem(holder, parent, "artifactId")
    }
  }

  override fun getShortName(): String {
    return "MavenParentMissedGroupIdArtefactIdInspection"
  }
}