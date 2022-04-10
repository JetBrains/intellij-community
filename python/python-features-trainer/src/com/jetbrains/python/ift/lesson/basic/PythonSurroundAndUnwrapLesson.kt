// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.basic

import com.jetbrains.python.PyBundle
import training.dsl.LessonSample
import training.dsl.parseLessonSample
import training.learn.lesson.general.SurroundAndUnwrapLesson

class PythonSurroundAndUnwrapLesson : SurroundAndUnwrapLesson() {
  override val sample: LessonSample = parseLessonSample("""
    def surround_and_unwrap_demo(debug):
        <select>if debug:
            print("Surround and Unwrap me!")</select>
    
  """.trimIndent())

  override val surroundItems = arrayOf("try", "except")

  override val lineShiftBeforeUnwrap = -2

  override val unwrapTryText = PyBundle.message("unwrap.try")
}
