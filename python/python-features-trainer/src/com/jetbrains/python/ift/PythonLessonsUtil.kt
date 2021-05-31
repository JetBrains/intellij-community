package com.jetbrains.python.ift

import com.intellij.openapi.project.Project
import com.jetbrains.python.inspections.PyInterpreterInspection
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.statistics.modules
import training.dsl.LessonContext
import training.ui.LearningUiManager

internal object PythonLessonsUtil {
  fun isPython3Installed(project: Project): Boolean {
    val sdk = project.pythonSdk ?: return false
    return PythonSdkFlavor.getFlavor(sdk)?.getLanguageLevel(sdk)?.isPy3K == true
  }

  fun LessonContext.showWarningIfPython3NotFound() {
    task {
      val callbackId = LearningUiManager.addCallback {
        PyInterpreterInspection.InterpreterSettingsQuickFix.showPythonInterpreterSettings(project, project.modules.first())
      }
      stateCheck { isPython3Installed(project) }
      showWarning(PythonLessonsBundle.message("python.3.required.warning.message", callbackId)) {
        !isPython3Installed(project)
      }
    }
  }
}