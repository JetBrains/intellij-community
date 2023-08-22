// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.refactorings

import training.dsl.LessonContext
import training.dsl.parseLessonSample
import training.dsl.restoreRefactoringOptionsInformer
import training.learn.lesson.general.refactorings.RefactoringMenuLessonBase

class PythonRefactorMenuLesson : RefactoringMenuLessonBase("Refactoring menu") {
  override val sample = parseLessonSample("""
    # Need to think about better sample!
    import random
    
    
    def foo(x):
        print(x + <select>random</select>)
  """.trimIndent())

  override val lessonContent: LessonContext.() -> Unit = {
    extractParameterTasks()
    restoreRefactoringOptionsInformer()
  }
}
