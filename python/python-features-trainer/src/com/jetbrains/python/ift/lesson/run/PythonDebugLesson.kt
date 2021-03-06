// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.run

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.LogicalPosition
import com.jetbrains.python.ift.PythonLessonsBundle
import training.dsl.LessonContext
import training.dsl.TaskTestContext
import training.dsl.highlightButtonById
import training.learn.lesson.general.run.CommonDebugLesson

class PythonDebugLesson : CommonDebugLesson("python.debug.workflow") {
  override val configurationName = PythonRunLessonsUtils.demoConfigurationName
  override val sample = PythonRunLessonsUtils.demoSample
  override var logicalPosition: LogicalPosition = LogicalPosition(4, 8)

  override val confNameForWatches = "PythonConfigurationType"
  override val quickEvaluationArgument = "int"
  override val expressionToBeEvaluated = "result/len(value)"
  override val debuggingMethodName = "find_average"
  override val methodForStepInto = "extract_number"
  override val stepIntoDirection = "←"

  override fun LessonContext.applyProgramChangeTasks() {
    highlightButtonById("Rerun")

    actionTask("Rerun") {
      before {
        mayBeStopped = true
      }
      proposeModificationRestore(afterFixText)
      PythonLessonsBundle.message("python.debug.workflow.rerun", icon(AllIcons.Actions.Restart), action(it))
    }
  }

  override val testScriptProperties: TaskTestContext.TestScriptProperties
    get() = TaskTestContext.TestScriptProperties(duration = 20)
}
