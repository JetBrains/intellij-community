// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.completion

import com.intellij.testGuiFramework.framework.GuiTestUtil.typeText
import com.intellij.testGuiFramework.impl.jList
import com.jetbrains.python.ift.PythonLessonsBundle
import training.learn.LessonsBundle
import training.learn.interfaces.Module
import training.learn.lesson.kimpl.KLesson
import training.learn.lesson.kimpl.LessonContext
import training.learn.lesson.kimpl.LessonUtil.checkExpectedStateOfEditor
import training.learn.lesson.kimpl.parseLessonSample

private const val completionSuffix = ".ifnn"

class PythonPostfixCompletionLesson(module: Module)
  : KLesson("Postfix completion", LessonsBundle.message("postfix.completion.lesson.name"), module, "Python") {
  private val sample = parseLessonSample("""
    movies_dict = {
        'title': 'Aviator',
        'year': '2005',
        'director': 'Martin Scorsese',
        'distributor': 'Miramax Films'
    }
    
    movies_dict.get('year')<caret>
  """.trimIndent())
  private val result = parseLessonSample("""
    movies_dict = {
        'title': 'Aviator',
        'year': '2005',
        'director': 'Martin Scorsese',
        'distributor': 'Miramax Films'
    }
    
    if movies_dict.get('year') is not None:
        <caret>
  """.trimIndent()).text

  override val lessonContent: LessonContext.() -> Unit
    get() = {
      prepareSample(sample)
      task {
        text(LessonsBundle.message("postfix.completion.type.template", code(completionSuffix)))
        triggerByListItemAndHighlight {
          it.toString() == completionSuffix
        }
        proposeRestore {
          checkExpectedStateOfEditor(sample) { completionSuffix.startsWith(it) }
        }
        test {
          ideFrame {
            typeText(completionSuffix)
          }
        }
      }
      task {
        text(PythonLessonsBundle.message("python.postfix.completion.select.item", code(completionSuffix)))
        stateCheck { editor.document.text == result }
        restoreByUi()
        test {
          ideFrame {
            jList("ifnn").item(0).doubleClick()
          }
        }
      }
    }

  //override val existedFile = PythonLangSupport.sandboxFile
}