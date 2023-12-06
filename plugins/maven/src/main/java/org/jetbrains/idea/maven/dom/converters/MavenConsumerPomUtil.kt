// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.converters

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.intellij.util.xml.ConvertContext
import com.intellij.util.xml.GenericDomValue
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.model.MavenDomParent
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel

object MavenConsumerPomUtil {

  @JvmStatic
  fun isAutomaticVersionFeatureEnabled(file: VirtualFile?, project: Project): Boolean {
    //https://issues.apache.org/jira/browse/MNG-624
    return StringUtil.compareVersionNumbers(MavenDomUtil.getMavenVersion(file, project), "4") >= 0;
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

  private fun getMavenParentElementFromContext(context: ConvertContext): MavenDomParent? {
    val mavenDomParent = context.invocationElement.parent as? MavenDomParent
    if (mavenDomParent != null) return mavenDomParent
    return (context.invocationElement.parent as? MavenDomProjectModel)?.mavenParent
  }
}