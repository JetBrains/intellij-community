// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.assistance

import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.impl.jList
import com.jetbrains.python.PyPsiBundle
import training.learn.interfaces.Module
import training.learn.lesson.general.assistance.EditorCodingAssistanceLesson
import training.learn.lesson.kimpl.LessonSample
import java.util.regex.Pattern

class PythonEditorCodingAssistanceLesson(module: Module, lang: String, sample: LessonSample) :
  EditorCodingAssistanceLesson(module, lang, sample) {

  override fun IdeFrameFixture.simulateErrorFixing() {
    jList(intentionDisplayName).item(intentionDisplayName).doubleClick()
    val importMethodPattern = Pattern.compile("""sin[\s\S]*math""")
    jList("cmath.sin()").clickItem(importMethodPattern)
  }

  override val fixedText: String = "from math import cos, exp, sin"

  override val intentionDisplayName: String
    get() = PyPsiBundle.message("QFIX.auto.import.import.this.name")

  override val variableNameToHighlight: String = "value"
}