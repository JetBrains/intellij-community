// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.featuresTrainer.ift.lesson.completion

import com.intellij.python.featuresTrainer.ift.PythonLessonsBundle
import training.dsl.LessonContext
import training.dsl.LessonUtil
import training.dsl.LessonUtil.checkExpectedStateOfEditor
import training.dsl.TaskContext
import training.dsl.parseLessonSample
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.util.isToStringContains

class PythonSmartCompletionLesson
  : KLesson("Smart completion", LessonsBundle.message("smart.completion.lesson.name")) {
  private val sample = parseLessonSample("""
    def f(x, file):
        x.append(file)
        x.rem<caret>
  """.trimIndent())

  override val lessonContent: LessonContext.() -> Unit
    get() {
      val methodName = "remove_duplicates"
      val insertedCode = "ove_duplicates()"
      return {
        prepareSample(sample)
        actionTask("CodeCompletion") {
          proposeRestoreMe()
          PythonLessonsBundle.message("python.smart.completion.try.basic.completion", action(it))
        }
        task("SmartTypeCompletion") {
          text(PythonLessonsBundle.message("python.smart.completion.use.smart.completion",
                                           code("x"), action(it)))
          triggerAndBorderHighlight().listItem { ui ->
            ui.isToStringContains(methodName)
          }
          proposeRestoreMe()
          test { actions(it) }
        }
        task {
          val result = LessonUtil.insertIntoSample(sample, insertedCode)
          text(PythonLessonsBundle.message("python.smart.completion.finish.completion", code(methodName)))
          restoreByUi()
          stateCheck {
            editor.document.text == result
          }
          test {
            ideFrame {
              jListContains(methodName).item(methodName).doubleClick()
            }
          }
        }
      }
    }

  private fun TaskContext.proposeRestoreMe() {
    proposeRestore {
      checkExpectedStateOfEditor(sample)
    }
  }

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(PythonLessonsBundle.message("python.smart.completion.help.link"),
         LessonUtil.getHelpLink("pycharm", "auto-completing-code.html#smart_type_matching_completion")),
  )
}