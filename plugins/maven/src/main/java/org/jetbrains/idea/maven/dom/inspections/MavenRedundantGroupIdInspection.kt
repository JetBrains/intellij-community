// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.inspections

import com.intellij.openapi.project.Project
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel

class MavenRedundantGroupIdInspection : AbstractMavenRedundantParentInspection() {
  override val elementName: String = "groupId"

  override fun getShortName(): String = "MavenRedundantGroupId"

  override fun getXmlTag(projectModel: MavenDomProjectModel): XmlTag? = projectModel.groupId.getXmlTag()

  override fun getSelfValue(projectModel: MavenDomProjectModel): String? = projectModel.groupId.stringValue

  override fun getParentValue(projectModel: MavenDomProjectModel, project: Project): String? = projectModel.mavenParent.groupId.stringValue

  override fun supportedForFile(file: XmlFile): Boolean= true
}
