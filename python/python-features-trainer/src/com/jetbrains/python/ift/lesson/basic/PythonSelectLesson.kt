// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.basic

import org.jetbrains.annotations.Nls
import training.learn.interfaces.Module
import training.learn.lesson.general.NewSelectLesson
import training.learn.lesson.kimpl.LessonSample
import training.learn.lesson.kimpl.parseLessonSample

class PythonSelectLesson(module: Module) : NewSelectLesson(module, "Python") {
  override val selectArgument = "\"$selectString\""
  override val selectCall = """some_method("$firstString", $selectArgument, "$thirdString")"""

  override val sample: LessonSample = parseLessonSample("""
    def some_method(first, second, third):
        print(first, second, third)
    
    
    def example_method(condition):
        <select id=1>if condition:
            print("$beginString")
            $selectCall
            print("$endString")</select>
        print("The end")
  """.trimIndent())
  override val selectIf = sample.getPosition(1).selection!!.let { sample.text.substring(it.first, it.second) }

  @Nls
  override val numberOfSelectsForWholeCall = 2
}