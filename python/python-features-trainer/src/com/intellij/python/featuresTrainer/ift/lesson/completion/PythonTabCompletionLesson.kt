// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.featuresTrainer.ift.lesson.completion

import com.intellij.python.featuresTrainer.ift.PythonLessonsBundle
import training.dsl.*
import training.dsl.LessonUtil.checkExpectedStateOfEditor
import training.learn.course.KLesson
import training.util.isToStringContains
import javax.swing.JList

class PythonTabCompletionLesson
  : KLesson("Tab completion", PythonLessonsBundle.message("python.tab.completion.lesson.name")) {
  private val template = parseLessonSample("""
    class Calculator:
        def __init__(self):
            self.current = 0
            self.total = 0
    
        def add(self, amount):
            self.current += amount
    
        def get_current(self):
            return self.<caret>
  """.trimIndent())

  private val sample = createFromTemplate(template, "current")

  private val isTotalItem = { item: Any -> item.isToStringContains("total") }

  override val lessonContent: LessonContext.() -> Unit
    get() {
      return {
        prepareSample(sample)
        task("CodeCompletion") {
          text(PythonLessonsBundle.message("python.tab.completion.start.completion",
                                           code("current"), code("total"), action(it)))
          triggerAndBorderHighlight().listItem { ui -> isTotalItem(ui) }
          proposeRestoreMe()
          test { actions(it) }
        }
        task {
          text(PythonLessonsBundle.message("python.tab.completion.select.item", code("total")))
          restoreState(delayMillis = defaultRestoreDelay) {
            (previous.ui as? JList<*>)?.let { ui ->
              !ui.isShowing || LessonUtil.findItem(ui, isTotalItem) == null
            } ?: true
          }
          stateCheck {
            selectNeededItem() ?: false
          }
          test {
            ideFrame {
              jListContains("total").item("total").click()
            }
          }
        }
        task {
          val result = LessonUtil.insertIntoSample(template, "total")
          text(PythonLessonsBundle.message("python.tab.completion.use.tab.completion",
                                           action("EditorEnter"), code("total"), code("current"),
                                           action("EditorTab")))

          trigger("EditorChooseLookupItemReplace") {
            editor.document.text == result
          }
          restoreAfterStateBecomeFalse {
            selectNeededItem()?.not() ?: true
          }
          test { invokeActionViaShortcut("TAB") }
        }
      }
    }

  private fun TaskRuntimeContext.selectNeededItem(): Boolean? {
    return (previous.ui as? JList<*>)?.let { ui ->
      if (!ui.isShowing) return false
      val selectedIndex = ui.selectedIndex
      selectedIndex != -1 && isTotalItem(ui.model.getElementAt(selectedIndex))
    }
  }

  private fun TaskContext.proposeRestoreMe() {
    proposeRestore {
      checkExpectedStateOfEditor(sample)
    }
  }

}