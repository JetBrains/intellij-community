// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.completion

import com.jetbrains.python.ift.PythonLessonsBundle
import training.dsl.LessonContext
import training.dsl.LessonUtil.checkExpectedStateOfEditor
import training.dsl.parseLessonSample
import training.learn.LessonsBundle
import training.learn.course.KLesson

private const val completionSuffix = ".ifnn"

class PythonPostfixCompletionLesson
  : KLesson("Postfix completion", LessonsBundle.message("postfix.completion.lesson.name")) {
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
          type(completionSuffix)
        }
      }
      task {
        text(PythonLessonsBundle.message("python.postfix.completion.select.item", code(completionSuffix)))
        stateCheck { editor.document.text == result }
        restoreByUi()
        test(waitEditorToBeReady = false) {
          ideFrame {
            jList("ifnn").item(0).doubleClick()
          }
        }
      }
    }

  //override val existedFile = PythonLangSupport.sandboxFile
}