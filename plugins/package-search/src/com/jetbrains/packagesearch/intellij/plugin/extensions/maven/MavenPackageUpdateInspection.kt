package com.jetbrains.packagesearch.intellij.plugin.extensions.maven

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlTag
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Package
import com.jetbrains.packagesearch.intellij.plugin.extensibility.PackageUpdateInspection
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.navigator.MavenNavigationUtil

class MavenPackageUpdateInspection : PackageUpdateInspection() {

    override fun getStaticDescription(): String? = PackageSearchBundle.getMessage("packagesearch.inspection.update.description.maven")

    override fun shouldCheckFile(file: PsiFile) = MavenDomUtil.isProjectFile(file)

    override fun getVersionElement(file: PsiFile, dependency: StandardV2Package): PsiElement? {
        val projectModel = MavenDomUtil.getMavenDomProjectModel(file.project, file.virtualFile) ?: return null
        val mavenDependency = MavenNavigationUtil.findDependency(projectModel, dependency.groupId, dependency.artifactId)
        val element = mavenDependency?.version?.xmlElement ?: return null

        return (element as XmlTag).value.textElements.first()
    }
}
