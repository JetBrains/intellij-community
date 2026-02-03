// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.converters

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlFile
import com.intellij.util.xml.ConvertContext
import com.intellij.util.xml.GenericDomValue
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.MavenDomUtil.isAtLeastMaven4
import org.jetbrains.idea.maven.dom.model.MavenDomParent
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel

object MavenConsumerPomUtil {

  @JvmStatic
  fun isAutomaticVersionFeatureEnabled(file: VirtualFile?, project: Project): Boolean {
    //https://issues.apache.org/jira/browse/MNG-624
    return isAtLeastMaven4(file, project);
  }

  @JvmStatic
  fun isAutomaticVersionFeatureEnabled(context: ConvertContext): Boolean {
    return isAutomaticVersionFeatureEnabled(context.file.virtualFile, context.project)
  }

  @JvmStatic
  fun getAutomaticParentVersion(context: ConvertContext): String? {
    return getDerivedPropertiesForConsumerPom(context) { it.version }
  }

  @JvmStatic
  fun getDerivedPropertiesForConsumerPom(context: ConvertContext, extractor: (MavenDomProjectModel) -> GenericDomValue<String>): String? {

    val parentElement = getMavenParentElementFromContext(context) ?: return null
    val artifactId = parentElement.artifactId.value
    val groupId = parentElement.groupId.value
    if (artifactId == null || groupId == null) return null

    return getDerivedParentPropertyForConsumerPom(context.file, artifactId, groupId, extractor)
  }

  @JvmStatic
  fun getDerivedParentPropertyForConsumerPom(currentPomFile: XmlFile,
                                             parentElementArtifactId: String,
                                             parentElementGroupId: String,
                                             extractor: (MavenDomProjectModel) -> GenericDomValue<String>): String? {
    val parentPsi = currentPomFile.parent?.parent?.findFile("pom.xml") as? XmlFile ?: return null
    val mavenParentDomPsiModel = MavenDomUtil.getMavenDomModel(parentPsi, MavenDomProjectModel::class.java) ?: return null
    val parentRealGroupId = mavenParentDomPsiModel.groupId.value ?: mavenParentDomPsiModel.mavenParent.groupId.value
    if (mavenParentDomPsiModel.artifactId.value == parentElementArtifactId && parentRealGroupId == parentElementGroupId) {
      return extractor(mavenParentDomPsiModel).value
    }
    return null
  }

  @JvmStatic
  fun getParentPomPropertyUsingRelativePath(context: ConvertContext,
                                            extractor: (MavenDomProjectModel) -> GenericDomValue<String>): String? {
    val parent = getMavenParentElementFromContext(context) ?: return null
    val parentPom = parent.relativePath.value ?: return null
    val parentPomDomModel = MavenDomUtil.getMavenDomModel(parentPom, MavenDomProjectModel::class.java) ?: return null
    return extractor(parentPomDomModel).value
  }

  private fun getMavenParentElementFromContext(context: ConvertContext): MavenDomParent? {
    val mavenDomParent = context.invocationElement.parent as? MavenDomParent
    if (mavenDomParent != null) return mavenDomParent
    return (context.invocationElement.parent as? MavenDomProjectModel)?.mavenParent
  }
}