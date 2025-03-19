// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.featuresTrainer.ift.lesson.run

import com.jetbrains.python.run.PythonRunConfiguration
import com.intellij.python.featuresTrainer.ift.PythonLessonsBundle
import training.dsl.*
import training.learn.lesson.general.run.CommonRunConfigurationLesson
import training.ui.LearningUiHighlightingManager

class PythonRunConfigurationLesson : CommonRunConfigurationLesson("python.run.configuration") {
  override val demoConfigurationName = "sandbox"
  
  override val sample: LessonSample = parseLessonSample("""
    import sys
    
    print('It is a run configurations sample')
    
    for s in sys.argv:
        print('Passed argument: ', s)
  """.trimIndent())


  override fun LessonContext.runTask() {
    task("RunClass") {
      before {
        LearningUiHighlightingManager.clearHighlights()
      }
      text(PythonLessonsBundle.message("python.run.configuration.lets.run", action(it)))
      timerCheck { configurations().isNotEmpty() }
      //Wait toolwindow
      checkToolWindowState("Run", true)
      test {
        actions(it)
      }
    }
  }

  override fun LessonContext.addAnotherRunConfiguration() {
    prepareRuntimeTask {
      addNewRunConfigurationFromContext { runConfiguration ->
        runConfiguration.name = demoWithParametersName
        if (runConfiguration is PythonRunConfiguration) {
          runConfiguration.setNameChangedByUser(true)
          runConfiguration.scriptParameters = "hello world"
        }
      }
    }
  }

  // Redefine the base class links:
  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(PythonLessonsBundle.message("python.run.configuration.help.link"),
         LessonUtil.getHelpLink("pycharm", "code-running-assistance-tutorial.html")),
  ) + super.helpLinks
}
