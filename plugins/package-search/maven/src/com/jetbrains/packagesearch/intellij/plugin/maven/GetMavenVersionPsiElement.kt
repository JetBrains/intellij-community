package com.jetbrains.packagesearch.intellij.plugin.maven

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.navigator.MavenNavigationUtil

internal fun getMavenVersionPsiElement(
  dependency: UnifiedDependency,
  file: PsiFile
): PsiElement? {
    val groupId = dependency.coordinates.groupId ?: return null
    val artifactId = dependency.coordinates.artifactId ?: return null

    val projectModel = MavenDomUtil.getMavenDomProjectModel(file.project, file.virtualFile) ?: return null

    val mavenDependency = MavenNavigationUtil.findDependency(projectModel, groupId, artifactId)
    val element = mavenDependency?.version?.xmlElement ?: return null

    return if (element is XmlTag) element.value.textElements.firstOrNull() else null
}
