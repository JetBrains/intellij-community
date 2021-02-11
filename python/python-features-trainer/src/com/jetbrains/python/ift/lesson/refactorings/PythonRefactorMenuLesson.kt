// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.refactorings

import training.learn.lesson.general.refactorings.RefactoringMenuLessonBase
import training.learn.lesson.kimpl.LessonContext
import training.learn.lesson.kimpl.parseLessonSample

class PythonRefactorMenuLesson : RefactoringMenuLessonBase("Refactoring menu", "Python") {
  private val sample = parseLessonSample("""
    # Need to think about better sample!
    import random
    
    
    def foo(x):
        print(x + <select>random</select>)
  """.trimIndent())

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)
    extractParameterTasks()
  }
}
