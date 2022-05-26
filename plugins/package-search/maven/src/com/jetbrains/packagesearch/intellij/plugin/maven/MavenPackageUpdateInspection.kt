package com.jetbrains.packagesearch.intellij.plugin.maven

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlTag
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageUpdateInspection
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.navigator.MavenNavigationUtil

internal class MavenPackageUpdateInspection : PackageUpdateInspection() {

    override fun getStaticDescription(): String = PackageSearchBundle.getMessage("packagesearch.inspection.upgrade.description.maven")

    override fun getVersionPsiElement(file: PsiFile, dependency: UnifiedDependency): PsiElement? {
        val groupId = dependency.coordinates.groupId ?: return null
        val artifactId = dependency.coordinates.artifactId ?: return null

        val projectModel = MavenDomUtil.getMavenDomProjectModel(file.project, file.virtualFile) ?: return null

        val mavenDependency = MavenNavigationUtil.findDependency(projectModel, groupId, artifactId)
        val element = mavenDependency?.version?.xmlElement ?: return null

        return (element as XmlTag).value.textElements.firstOrNull()
    }
}
