package com.jetbrains.packagesearch.intellij.plugin.maven

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageUpdateInspection
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageVersionRangeInspection
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.navigator.MavenNavigationUtil

private fun getMavenVersionPsiElement(
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

internal class MavenPackageUpdateInspection : PackageUpdateInspection() {

    override fun getStaticDescription(): String = PackageSearchBundle.getMessage("packagesearch.inspection.upgrade.description.maven")

    override fun getVersionPsiElement(file: PsiFile, dependency: UnifiedDependency): PsiElement? =
        getMavenVersionPsiElement(dependency, file)

}

internal class MavenPackageVersionRangeInspection : PackageVersionRangeInspection() {

    override fun getStaticDescription(): String = PackageSearchBundle.getMessage("packagesearch.inspection.range.description.maven")

    override fun getVersionPsiElement(file: PsiFile, dependency: UnifiedDependency): PsiElement? =
        getMavenVersionPsiElement(dependency, file)

}
