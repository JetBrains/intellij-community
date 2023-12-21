// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.featuresTrainer.ift.lesson.completion

import com.intellij.python.featuresTrainer.ift.PythonLessonsBundle
import com.intellij.python.featuresTrainer.ift.PythonLessonsUtil.showWarningIfPython3NotFound
import training.dsl.LessonContext
import training.dsl.LessonUtil
import training.dsl.LessonUtil.checkExpectedStateOfEditor
import training.dsl.parseLessonSample
import training.learn.course.KLesson
import training.util.isToStringContains

class FStringCompletionLesson
  : KLesson("completion.f.string", PythonLessonsBundle.message("python.f.string.completion.lesson.name")) {
  private val template = """
    import sys
    
    class Car:
        def __init__(self, speed=0):
            self.speed = speed
            self.odometer = 0
            self.time = 0
        def say_state(self):
            print("I'm going kph!".format(self.speed))
        def accelerate(self):
            self.speed += 5
        def brake(self):
            self.speed -= 5
        def step(self):
            self.odometer += self.speed
            self.time += 1
        def average_speed(self):
            return self.odometer / self.time
    if __name__ == '__main__':
        my_car_show_distance = sys.argv[1]
        my_car = Car()
        print("I'm a car!")
        while True:
            action = input("What should I do? [A]ccelerate, [B]rake, "
                     "show [O]dometer, or show average [S]peed?").upper()
            if action not in "ABOS" or len(action) != 1:
                print("I don't know how to do that")
            if my_car_show_distance == "yes":
                print(<f-place>"The car has driven <caret> kilometers")
    """.trimIndent() + '\n'

  private val completionItem = "my_car"

  private val sample = parseLessonSample(template.replace("<f-place>", ""))

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)
    showWarningIfPython3NotFound()

    task("{my") {
      text(PythonLessonsBundle.message("python.f.string.completion.type.prefix", code(it)))
      runtimeText {
        val prefixTyped = checkExpectedStateOfEditor(sample) { change ->
          "{my_car".startsWith(change) && change.startsWith(it)
        } == null
        if (prefixTyped) PythonLessonsBundle.message("python.f.string.completion.invoke.manually", action("CodeCompletion")) else null
      }
      triggerUI().listItem { item ->
        item.isToStringContains(completionItem)
      }
      proposeRestore {
        checkExpectedStateOfEditor(sample) { change ->
          "{my_car".startsWith(change)
        }
      }
      test { type(it) }
    }
    task {
      text(PythonLessonsBundle.message("python.f.string.completion.complete.it", code(completionItem), action("EditorChooseLookupItem")))
      val result = template.replace("<f-place>", "f").replace("<caret>", "{$completionItem}")
      restoreByUi()
      stateCheck {
        editor.document.text == result
      }
      test(waitEditorToBeReady = false) { invokeActionViaShortcut("ENTER") }
    }
    text(PythonLessonsBundle.message("python.f.string.completion.result.message"))
  }

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(PythonLessonsBundle.message("python.f.string.completion.help.link"),
         LessonUtil.getHelpLink("pycharm", "auto-completing-code.html#f-string-completion")),
  )
}
