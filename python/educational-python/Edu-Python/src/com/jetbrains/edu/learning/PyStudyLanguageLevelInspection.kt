package com.jetbrains.edu.learning

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiUtilCore
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.sdk.PythonSdkType
import org.jetbrains.annotations.Nls

class PyStudyLanguageLevelInspection : PyInspection() {
  @Nls
  override fun getDisplayName(): String {
    return "Unsupported language level for a course"
  }

  override fun buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean,
                            session: LocalInspectionToolSession): PsiElementVisitor {
    return Visitor(holder, session)
  }

  class Visitor(holder: ProblemsHolder?,
                session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

    override fun visitPyFile(node: PyFile) {
      super.visitPyFile(node)
      val module = ModuleUtilCore.findModuleForPsiElement(node)
      val virtualFile = PsiUtilCore.getVirtualFile(node)
      if (module != null && virtualFile != null) {
        val sdk = PythonSdkType.findPythonSdk(module)
        if (sdk != null) {
          val projectLanguageLevel = PythonSdkType.getLanguageLevelForSdk(sdk)
          val project = node.project
          val course = StudyTaskManager.getInstance(project).course ?: return
          checkIfLanguageLevelSupported(course, projectLanguageLevel, node)
        }
      }
    }

    fun checkIfLanguageLevelSupported(course: Course, languageLevel: LanguageLevel, node: PyFile) {
      if (course.isAdaptive) {
        if(!languageLevel.isPy3K) {
          registerProblem(node, "Adaptive courses support Python 3 only", ConfigureInterpreterFix())
        }
      }
    }
  }
}


private class ConfigureInterpreterFix : LocalQuickFix {
  override fun getName(): String {
    return "Configure python interpreter"
  }

  override fun getFamilyName(): String {
    return "Configure python interpreter"
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    ApplicationManager.getApplication()
      .invokeLater { ShowSettingsUtil.getInstance().showSettingsDialog(project, "Project Interpreter") }
  }
}

