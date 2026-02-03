// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.XmlSuppressableInspectionTool
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.intellij.util.xml.DomManager
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.model.MavenDomParent
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.server.MavenDistributionsCache
import org.jetbrains.idea.maven.server.isMaven4

class Maven4RedundantParentCoordinatesInspection : XmlSuppressableInspectionTool() {

  override fun getShortName(): String = "Maven4RedundantParentCoordinates"

  @Suppress("NULLABILITY_MISMATCH_BASED_ON_EXPLICIT_TYPE_ARGUMENTS_FOR_JAVA")
  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor?>? {
    val project = file.project

    if (file is XmlFile && file.isPhysical()) {
      val dist = MavenDistributionsCache.getInstance(file.project).getMavenDistribution(file.virtualFile)
      if (!dist.isMaven4()) return null
      val dom =
        DomManager.getDomManager(file.getProject()).getFileElement(file, MavenDomProjectModel::class.java)
        ?: return null

      val model = dom.rootElement
      val declaredParentGroupId = model.mavenParent.groupId.stringValue
      val declaredParentVersion = model.mavenParent.version.stringValue
      val declaredParentArtifactId = model.mavenParent.artifactId.stringValue
      if (declaredParentGroupId.isNullOrEmpty() && declaredParentVersion.isNullOrEmpty()) return null
      val parentFile = model.mavenParent.relativePath.value ?: file.parent?.parent?.findFile("pom.xml") ?: return null
      if (parentFile is XmlFile && parentFile.isPhysical()) {
        val parentDom =
          DomManager.getDomManager(project).getFileElement(parentFile, MavenDomProjectModel::class.java)
          ?: return null
        val parentModel = parentDom.rootElement
        val parentGroupId = parentModel.groupId.stringValue
        val parentVersion = parentModel.version.stringValue
        val parentArtifactId = parentModel.artifactId.stringValue

        if (declaredParentGroupId != parentGroupId) return null
        if (declaredParentVersion != parentVersion) return null
        if (declaredParentArtifactId != parentArtifactId) return null
        val fix = getCleanParentFix(model.mavenParent)
        return listOf(model.mavenParent.groupId,
                      model.mavenParent.artifactId,
                      model.mavenParent.version)
          .filter { it.exists() }
          .mapNotNull { it.getXmlTag() }
          .map {
            manager.createProblemDescriptor(it,
                                            MavenDomBundle.message("inspection.redundant.parent.coordinates.maven.4"),
                                            fix,
                                            ProblemHighlightType.WARNING,
                                            isOnTheFly)
          }.toTypedArray()


      }
    }
    return null
  }

  private fun getCleanParentFix(mavenParent: MavenDomParent): LocalQuickFix {
    return object : LocalQuickFix {
      override fun getFamilyName(): @IntentionFamilyName String {
        return MavenDomBundle.message("inspection.redundant.parent.coordinates.maven.4")
      }

      override fun getName(): @IntentionName String {
        return MavenDomBundle.message("inspection.redundant.parent.coordinates.maven.4.clean")
      }

      override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        mavenParent.groupId.xmlTag?.delete()
        mavenParent.artifactId.xmlTag?.delete()
        mavenParent.version.xmlTag?.delete()
        mavenParent.xmlTag?.collapseIfEmpty()
      }
    }
  }
}