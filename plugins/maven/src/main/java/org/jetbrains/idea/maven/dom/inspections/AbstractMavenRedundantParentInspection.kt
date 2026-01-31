// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.XmlSuppressableInspectionTool
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.DomManager
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel

abstract class AbstractMavenRedundantParentInspection : XmlSuppressableInspectionTool() {
  override fun getGroupDisplayName(): String {
    return MavenDomBundle.message("inspection.group")
  }

  protected abstract val elementName: String
  abstract override fun getShortName(): String

  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor?>? {
    if (file is XmlFile && file.isPhysical()) {
      val model =
        DomManager.getDomManager(file.getProject()).getFileElement<MavenDomProjectModel?>(file, MavenDomProjectModel::class.java)


      if (model != null) {
        val projectModel = model.getRootElement()
        if (projectModel == null) return null

        if (!supportedForFile(file)) {
          return null
        }

        val selfValue = getSelfValue(projectModel)
        if (selfValue != null && !selfValue.isEmpty()) {
          val parentValue = getParentValue(projectModel, file.project)
          if (selfValue == parentValue) {
            val xmlTag = getXmlTag(projectModel) ?: return null

            val fix: LocalQuickFix = object : LocalQuickFix {


              override fun getFamilyName(): @IntentionFamilyName String {
                return MavenDomBundle.message("inspection.redundant.element.fix", elementName)
              }

              override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                descriptor.getPsiElement().delete()
              }
            }

            return arrayOf(manager.createProblemDescriptor(xmlTag,
                                                           MavenDomBundle.message("inspection.redundant.element.fix.description", elementName),
                                                           fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                           isOnTheFly)
            )
          }
        }
      }
    }

    return null
  }

  abstract fun getXmlTag(projectModel: MavenDomProjectModel): XmlTag?

  abstract fun getSelfValue(projectModel: MavenDomProjectModel): String?
  abstract fun getParentValue(projectModel: MavenDomProjectModel, project: Project): String?

  protected abstract fun supportedForFile(file: XmlFile): Boolean

}