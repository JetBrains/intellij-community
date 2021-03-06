// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.completion

import com.jetbrains.python.ift.PythonLessonsBundle
import training.dsl.LessonContext
import training.dsl.LessonUtil
import training.dsl.LessonUtil.checkExpectedStateOfEditor
import training.dsl.TaskContext
import training.dsl.parseLessonSample
import training.learn.course.KLesson

class PythonSmartCompletionLesson
  : KLesson("Smart completion", PythonLessonsBundle.message("python.smart.completion.lesson.name")) {
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
          triggerByListItemAndHighlight { ui ->
            ui.toString().contains(methodName)
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
}