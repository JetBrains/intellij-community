package com.jetbrains.python.ift.lesson.basic

import com.jetbrains.python.PyPsiBundle
import training.learn.lesson.general.ContextActionsLesson
import training.learn.lesson.kimpl.LessonSample
import training.learn.lesson.kimpl.parseLessonSample

class PythonContextActionsLesson : ContextActionsLesson("Python") {
  override val sample: LessonSample = parseLessonSample("""
    def method_with_unused_parameter(used, <caret>redundant):
        print("It is used parameter: " + str(used))
    
    
    def intention_example(a, b):
        if not (a and b):
            return 1
        return 2
    
    
    method_with_unused_parameter("first", "second")
    method_with_unused_parameter("used", "unused")
    intention_example(True, False)
  """.trimIndent())

  override val warningQuickFix: String = PyPsiBundle.message("QFIX.NAME.remove.parameter")
  override val warningPossibleArea: String = "redundant"

  override val intentionText: String = PyPsiBundle.message("INTN.NAME.demorgan.law")
  override val intentionCaret: String = "and b"
  override val intentionPossibleArea: String = "a and "
}