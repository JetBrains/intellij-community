// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.assistance

import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.ift.PythonLessonsBundle
import training.commands.kotlin.TaskContext
import training.learn.lesson.general.assistance.EditorCodingAssistanceLesson
import training.learn.lesson.kimpl.LessonSample

class PythonEditorCodingAssistanceLesson(lang: String, sample: LessonSample) :
  EditorCodingAssistanceLesson(lang, sample) {
  override val errorIntentionText: String
    get() = PyPsiBundle.message("QFIX.auto.import.import.name", "math")
  private val errorAlternateIntentionText: String
    get() = PyPsiBundle.message("QFIX.auto.import.import.this.name")
  override val warningIntentionText: String
    get() = PyPsiBundle.message("QFIX.NAME.remove.argument")

  override val errorFixedText: String = "import math"
  override val warningFixedText: String = "cat.say_meow()"

  override val variableNameToHighlight: String = "happiness"

  override fun isHighlightedListItem(item: String): Boolean {
    return super.isHighlightedListItem(item) || item == errorAlternateIntentionText
  }

  override fun TaskContext.addFixErrorTaskText() {
    text(PythonLessonsBundle.message("python.editor.coding.assistance.fix.error", action("ShowIntentionActions"),
                                     strong(errorIntentionText), strong(errorAlternateIntentionText)))
  }
}