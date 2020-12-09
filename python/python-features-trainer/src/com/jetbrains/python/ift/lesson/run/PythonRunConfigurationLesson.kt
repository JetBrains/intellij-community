// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.run

import com.jetbrains.python.ift.PythonLessonsBundle
import training.learn.interfaces.Module
import training.learn.lesson.general.run.CommonRunConfigurationLesson
import training.learn.lesson.kimpl.LessonContext
import training.learn.lesson.kimpl.LessonSample
import training.learn.lesson.kimpl.toolWindowShowed

class PythonRunConfigurationLesson(module: Module) : CommonRunConfigurationLesson(module, "python.run.configuration", "Python") {
  override val sample: LessonSample = PythonRunLessonsUtils.demoSample
  override val demoConfigurationName = PythonRunLessonsUtils.demoConfigurationName

  override fun LessonContext.runTask() {
    task("RunClass") {
      text(PythonLessonsBundle.message("python.run.configuration.lets.run", action(it)))
      //Wait toolwindow
      toolWindowShowed("Run")
      stateCheck {
        configurations().isNotEmpty()
      }
      test {
        actions(it)
      }
    }
  }
}
