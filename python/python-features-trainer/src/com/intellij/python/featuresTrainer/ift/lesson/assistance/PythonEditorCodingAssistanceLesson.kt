// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.featuresTrainer.ift.lesson.assistance

import com.intellij.codeInsight.daemon.impl.runActionCustomShortcutSet
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.jetbrains.python.PyPsiBundle
import com.intellij.python.featuresTrainer.ift.PythonLessonsBundle
import training.dsl.LearningDslBase
import training.dsl.LessonSample
import training.dsl.LessonUtil
import training.dsl.TaskTestContext
import training.learn.lesson.general.assistance.EditorCodingAssistanceLesson

class PythonEditorCodingAssistanceLesson(sample: LessonSample) :
  EditorCodingAssistanceLesson(sample) {

  // TODO: remove when PY-53671 will be fixed
  override val testScriptProperties = TaskTestContext.TestScriptProperties(skipTesting = true)

  override val errorIntentionText: String
    get() = PyPsiBundle.message("QFIX.auto.import.import.name", "math")
  private val errorAlternateIntentionText: String
    get() = PyPsiBundle.message("QFIX.auto.import.import.this.name")
  override val warningIntentionText: String
    get() = PyPsiBundle.message("QFIX.NAME.make.list")

  override val errorFixedText: String = "import math"
  override val warningFixedText: String = """["I'm", "very", "happy"]"""

  override val variableNameToHighlight: String = "happiness"

  override fun LearningDslBase.getFixErrorTaskText(): String {
    return PythonLessonsBundle.message("python.editor.coding.assistance.fix.error", action("ShowIntentionActions"),
                                       strong(errorIntentionText),
                                       strong(errorAlternateIntentionText))
  }

  override fun getFixWarningText(): String {
    val shortcut = runActionCustomShortcutSet.shortcuts.first() as KeyboardShortcut
    return PythonLessonsBundle.message("python.editor.coding.assistance.press.to.fix", LessonUtil.rawKeyStroke(shortcut.firstKeyStroke))
  }
}