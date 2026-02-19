package com.intellij.python.featuresTrainer.ift.lesson.basic

import com.jetbrains.python.PyPsiBundle
import training.dsl.LessonSample
import training.dsl.parseLessonSample
import training.learn.lesson.general.ContextActionsLesson

class PythonContextActionsLesson : ContextActionsLesson() {
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