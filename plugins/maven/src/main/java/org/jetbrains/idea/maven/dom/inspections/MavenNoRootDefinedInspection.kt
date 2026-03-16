// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.inspections

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import com.intellij.util.xml.DomManager
import com.intellij.util.xml.highlighting.BasicDomElementsInspection
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.model.MavenConstants.MODEL_VERSION_4_0_0
import org.jetbrains.idea.maven.project.MavenProjectsManager
import kotlin.io.path.isDirectory

class MavenNoRootDefinedInspection : BasicDomElementsInspection<MavenDomProjectModel?>(MavenDomProjectModel::class.java) {
  override fun getGroupDisplayName(): String {
    return MavenDomBundle.message("inspection.group")
  }

  override fun getDefaultLevel(): HighlightDisplayLevel {
    return HighlightDisplayLevel.WARNING
  }

  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor?>? {
    if (file is XmlFile && file.isPhysical()) {
      val projectManager = MavenProjectsManager.getInstanceIfCreated(file.project) ?: return null
      val rootMavenProject = projectManager.rootProjects.singleOrNull { file.virtualFile.equals(it.file) } ?: return null
      val model =
        DomManager.getDomManager(file.getProject()).getFileElement<MavenDomProjectModel?>(file, MavenDomProjectModel::class.java)
        ?: return null

      @Suppress("USELESS_ELVIS")
      //KTLC-284

      val rootElement = model.rootElement ?: return null
      val psiToShowError = model.rootTag?.children?.takeWhile { it !is XmlTag && it !is XmlText } ?: return null

      if (rootMavenProject.file.parent.toNioPath().resolve(".mvn").isDirectory()) return null
      if (rootElement.modelVersion.stringValue == MODEL_VERSION_4_0_0) return null
      if (model.rootTag?.getAttributeValue("root")?.toBoolean() == true) {
        return null
      }


      return psiToShowError.map {
        manager.createProblemDescriptor(it,
                                        MavenDomBundle.message("inspection.absence.root.dir.description"),
                                        arrayOf(fixAddRootTag), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                        isOnTheFly, false)
      }.toTypedArray()
    }

    return null
  }

  private val fixAddRootTag: LocalQuickFix = object : LocalQuickFix {
    override fun getName(): @IntentionName String {
      return MavenDomBundle.message("inspection.absence.dir.fix.add.root")
    }

    override fun getFamilyName(): @IntentionFamilyName String {
      return MavenDomBundle.message("inspection.absence.root.dir.description")
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val projectTag = findRootTag(descriptor.psiElement)
      projectTag?.setAttribute("root", "true")
    }

    fun findRootTag(psiElement: PsiElement?): XmlTag? {
      if (psiElement == null) return null
      if (psiElement is XmlTag) return psiElement
      return findRootTag(psiElement.parent)
    }
  }

}