package com.jetbrains.python.ift

import com.jetbrains.python.inspections.PyInterpreterInspection
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.statistics.modules
import training.dsl.LessonContext
import training.dsl.TaskRuntimeContext
import training.ui.LearningUiManager

internal object PythonLessonsUtil {
  fun LessonContext.showWarningIfPython3NotFound() {
    fun TaskRuntimeContext.isPython3Installed(): Boolean {
      val sdk = project.pythonSdk ?: return false
      return PythonSdkFlavor.getFlavor(sdk)?.getLanguageLevel(sdk)?.isPy3K == true
    }

    task {
      val callbackId = LearningUiManager.addCallback {
        PyInterpreterInspection.InterpreterSettingsQuickFix.showPythonInterpreterSettings(project, project.modules.first())
      }
      stateCheck { isPython3Installed() }
      showWarning(PythonLessonsBundle.message("python.3.required.warning.message", callbackId)) {
        !isPython3Installed()
      }
    }
  }
}