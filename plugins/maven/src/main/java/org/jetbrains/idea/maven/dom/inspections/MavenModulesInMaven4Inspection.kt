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
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.xml.XmlFile
import com.intellij.util.xml.DomManager
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel

class MavenModulesInMaven4Inspection : XmlSuppressableInspectionTool() {

  override fun getShortName(): String = "MavenModulesInMaven4"

  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor?>? {
    if (file is XmlFile && file.isPhysical()) {

      if (!MavenDomUtil.isProjectFileWithModel410(file)) return null
      val dom =
        DomManager.getDomManager(file.getProject()).getFileElement(file, MavenDomProjectModel::class.java)
        ?: return null

      val model = dom.rootElement
      if (!model.modules.exists()) return null
      val tag = model.modules.xmlTag?:return null
      val fix = replaceModulesToSubprojects(model)



      return arrayOf(
          manager.createProblemDescriptor(
            tag,
            MavenDomBundle.message("inspection.modules.tag.in.maven.4"),
            fix,
            ProblemHighlightType.WARNING,
            isOnTheFly
          )
      )
    }
    return null
  }

  private fun replaceModulesToSubprojects(model: MavenDomProjectModel): LocalQuickFix {
    return object: LocalQuickFix {
      override fun getFamilyName(): @IntentionFamilyName String {
        return MavenDomBundle.message("inspection.modules.tag.in.maven.4")
      }

      override fun getName(): @IntentionName String {
        return MavenDomBundle.message("inspection.modules.tag.in.maven.4.name")
      }

      override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val modulesList = model.modules.modules.map {
          it.stringValue
        }

        val factory = XmlElementFactory.getInstance(project)
        val subprojectsTag = factory.createTagFromText("<subprojects/>")
        modulesList.forEach {
          val tag = factory.createTagFromText("<subproject>$it</subproject>")
          subprojectsTag.addSubTag(tag, false)

        }
        model.modules.xmlTag?.replace(subprojectsTag)
      }

    }
  }
}
