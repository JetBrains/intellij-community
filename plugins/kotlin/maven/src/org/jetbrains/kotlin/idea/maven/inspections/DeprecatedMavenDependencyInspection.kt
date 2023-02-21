// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.maven.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.util.text.VersionComparatorUtil
import com.intellij.util.xml.DomFileElement
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomElementsInspection
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.idea.inspections.ReplaceStringInDocumentFix
import org.jetbrains.kotlin.idea.maven.PomFile
import org.jetbrains.kotlin.idea.maven.findDependencies
import org.jetbrains.kotlin.idea.versions.DEPRECATED_LIBRARIES_INFORMATION

class DeprecatedMavenDependencyInspection :
    DomElementsInspection<MavenDomProjectModel>(MavenDomProjectModel::class.java), CleanupLocalInspectionTool {

    override fun checkFileElement(domFileElement: DomFileElement<MavenDomProjectModel>, holder: DomElementAnnotationHolder) {
        val file = domFileElement.file
        val module = domFileElement.module ?: return
        val manager = MavenProjectsManager.getInstance(module.project) ?: return
        val mavenProject = manager.findProject(module) ?: return

        val pomFile = PomFile.forFileOrNull(file) ?: return

        for (libInfo in DEPRECATED_LIBRARIES_INFORMATION) {
            val libMavenId = MavenId(libInfo.old.groupId, libInfo.old.name, null)

            val moduleDependencies = pomFile.findDependencies(libMavenId)
                .filter {
                    val libVersion =
                        mavenProject.findDependencies(libInfo.old.groupId, libInfo.old.name).map { it.version }.distinct().singleOrNull()
                    libVersion != null && VersionComparatorUtil.COMPARATOR.compare(libVersion, libInfo.outdatedAfterVersion) >= 0
                }

            val dependencyManagementDependencies = pomFile.domModel.dependencyManagement.dependencies.findDependencies(libMavenId).filter {
                val version = it.version?.stringValue
                version != null && VersionComparatorUtil.COMPARATOR.compare(version, libInfo.outdatedAfterVersion) >= 0
            }

            for (dependency in moduleDependencies + dependencyManagementDependencies) {
                val xmlElement = dependency.artifactId.xmlElement
                if (xmlElement != null) {
                    val fix = ReplaceStringInDocumentFix(xmlElement, libInfo.old.name, libInfo.new.name)

                    holder.createProblem(
                        dependency.artifactId,
                        ProblemHighlightType.LIKE_DEPRECATED,
                        libInfo.message,
                        null,
                        fix
                    )
                }
            }
        }
    }
}