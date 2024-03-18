// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.featuresTrainer.ift.lesson.run

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.python.featuresTrainer.ift.PythonLessonsBundle
import training.dsl.*
import training.learn.lesson.general.run.CommonDebugLesson

class PythonDebugLesson : CommonDebugLesson("python.debug.workflow") {
  override val configurationName = "sandbox"
  override var logicalPosition: LogicalPosition = LogicalPosition(4, 8)
  override val confNameForWatches = "PythonConfigurationType"

  override val quickEvaluationArgument = "int"
  override val debuggingMethodName = "find_average"
  override val methodForStepInto = "extract_number"
  override val stepIntoDirectionToRight = false


  override val sample = parseLessonSample("""
    def find_average(value):
        check_input(value)
        result = 0
        for s in value:
            <caret>result += <select id=1>validate_number(extract_number(remove_quotes(s)))</select>
        <caret id=3/>return <select id=4>result / len(value)</select>
    
    
    def prepare_values():
        return ["'apple 1'", "orange 2", "'tomato 3'"]
    
    
    def extract_number(s):
        return int(<select id=2>s.split()[0]</select>)
    
    
    def check_input(value):
        if (value is None) or (len(value) == 0):
            raise ValueError(value)
    
    
    def remove_quotes(s):
        if len(s) > 1 and s[0] == "'" and s[-1] == "'":
            return s[1:-1]
        return s
    
    
    def validate_number(number):
        if number < 0:
            raise ValueError(number)
        return number
    
    
    average = find_average(prepare_values())
    print("The average is ", average)
  """.trimIndent())


  override val breakpointXRange: (width: Int) -> IntRange = { IntRange(13, it - 17) }

  override fun LessonContext.applyProgramChangeTasks() {
    highlightButtonById("Rerun")

    actionTask("Rerun") {
      before {
        sessionPaused = false
      }
      proposeModificationRestore(afterFixText)
      PythonLessonsBundle.message("python.debug.workflow.rerun", icon(AllIcons.Actions.RestartDebugger), action(it))
    }

    task {
      stateCheck {
        sessionPaused
      }
    }
  }

  override val testScriptProperties: TaskTestContext.TestScriptProperties
    get() = TaskTestContext.TestScriptProperties(duration = 40)

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(PythonLessonsBundle.message("python.debug.workflow.help.link"),
         LessonUtil.getHelpLink("pycharm", "part-1-debugging-python-code.html")),
  ) + super.helpLinks
}
