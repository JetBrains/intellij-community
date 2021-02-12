// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.run

import com.jetbrains.python.ift.PythonLessonsBundle
import training.dsl.LessonContext
import training.dsl.LessonSample
import training.dsl.checkToolWindowState
import training.learn.lesson.general.run.CommonRunConfigurationLesson

class PythonRunConfigurationLesson : CommonRunConfigurationLesson("python.run.configuration") {
  override val sample: LessonSample = PythonRunLessonsUtils.demoSample
  override val demoConfigurationName = PythonRunLessonsUtils.demoConfigurationName

  override fun LessonContext.runTask() {
    task("RunClass") {
      text(PythonLessonsBundle.message("python.run.configuration.lets.run", action(it)))
      //Wait toolwindow
      checkToolWindowState("Run", true)
      stateCheck {
        configurations().isNotEmpty()
      }
      test {
        actions(it)
      }
    }
  }
}
