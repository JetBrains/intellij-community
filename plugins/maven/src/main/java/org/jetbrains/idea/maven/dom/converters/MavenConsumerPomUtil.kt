// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.converters

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
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

    val parentPom = parentElement.relativePath.value ?: return null
    return getDerivedParentPropertyForConsumerPom(parentPom, artifactId, groupId, extractor)
  }

  /**
   * Reads a property of the parent POM at [parentPomFile], if that POM has the declared parent coordinates.
   *
   * The parent POM can inherit the property. Then the value comes from the parent chain, through the converter
   * of the property. [RecursionManager] stops a cycle in that chain.
   */
  @JvmStatic
  fun getDerivedParentPropertyForConsumerPom(
    parentPomFile: PsiFile,
    parentElementArtifactId: String,
    parentElementGroupId: String,
    extractor: (MavenDomProjectModel) -> GenericDomValue<String>,
  ): String? {
    val mavenParentDomPsiModel = MavenDomUtil.getMavenDomModel(parentPomFile, MavenDomProjectModel::class.java) ?: return null
    if (mavenParentDomPsiModel.artifactId.value != parentElementArtifactId) return null
    val parentRealGroupId = mavenParentDomPsiModel.groupId.value ?: mavenParentDomPsiModel.mavenParent.groupId.value
    if (parentRealGroupId != parentElementGroupId) return null
    return RecursionManager.doPreventingRecursion(parentPomFile, false) { extractor(mavenParentDomPsiModel).value }
  }

  /**
   * Maven 4 takes an absent parent property from the POM at the relative path.
   * The default relative path is `../pom.xml`.
   * A POM can also inherit the property. Then the search continues up the parent chain.
   *
   * @param inheritedFromParent set it to true for a property that Maven inherits, such as the `groupId`.
   */
  @JvmStatic
  fun getParentPomPropertyUsingRelativePath(
    context: ConvertContext,
    inheritedFromParent: Boolean = false,
    extractor: (MavenDomProjectModel) -> GenericDomValue<String>,
  ): String? {
    val parent = getMavenParentElementFromContext(context) ?: return null
    var parentPom = parent.relativePath.value ?: return null
    val visitedPoms = mutableSetOf<PsiFile>(context.file)
    while (visitedPoms.add(parentPom)) {
      val parentPomDomModel = MavenDomUtil.getMavenDomModel(parentPom, MavenDomProjectModel::class.java) ?: return null
      extractor(parentPomDomModel).value?.let { return it }
      if (!inheritedFromParent) return null
      parentPom = parentPomDomModel.mavenParent.relativePath.value ?: return null
    }
    return null
  }

  private fun getMavenParentElementFromContext(context: ConvertContext): MavenDomParent? {
    val mavenDomParent = context.invocationElement.parent as? MavenDomParent
    if (mavenDomParent != null) return mavenDomParent
    return (context.invocationElement.parent as? MavenDomProjectModel)?.mavenParent
  }
}