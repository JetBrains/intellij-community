// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.inspections

import com.intellij.openapi.project.Project
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.server.MavenDistribution


class MavenRedundantVersionInspection : AbstractMavenRedundantParentInspection() {
  override val elementName: String = "version"

  override fun getShortName(): String = "MavenRedundantVersion"

  override fun getXmlTag(projectModel: MavenDomProjectModel): XmlTag? = projectModel.version.getXmlTag()

  override fun getSelfValue(projectModel: MavenDomProjectModel): String? = projectModel.version.stringValue

  override fun getParentValue(projectModel: MavenDomProjectModel, project: Project): String? = projectModel.mavenParent.version.stringValue

  override fun supportedForFile(file: XmlFile): Boolean = true
}
