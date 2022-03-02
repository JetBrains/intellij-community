// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.refactorings

import com.intellij.icons.AllIcons
import com.intellij.refactoring.RefactoringBundle
import com.jetbrains.python.ift.PythonLessonsBundle
import training.dsl.*
import training.dsl.LessonUtil.checkExpectedStateOfEditor
import training.learn.course.KLesson
import training.util.isToStringContains
import javax.swing.JLabel
import javax.swing.JPanel

class PythonInPlaceRefactoringLesson
  : KLesson("refactoring.in.place", PythonLessonsBundle.message("python.in.place.refactoring.lesson.name")) {
  private val template = """
    def fibonacci(stop):
        first = 0
        <name><caret> = 1
        while <name> < stop:
            print(<name>)
            first, <name> = <name>, first + <name>

    n = int(input("n = "))
    fibonacci(n)
  """.trimIndent() + '\n'

  private val variableName = "s"

  private val sample = parseLessonSample(template.replace("<name>", variableName))

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)
    fun TaskRuntimeContext.checkFirstChange() =
      checkExpectedStateOfEditor(sample) { change -> "econd".startsWith(change) }

    task {
      text(PythonLessonsBundle.message("python.in.place.refactoring.start.type.new.name", code(variableName), code("second")))
      stateCheck {
        editor.document.text != sample.text && checkFirstChange() == null
      }
      proposeRestore {
        checkFirstChange()
      }
      test { type("econd") }
    }

    task("ShowIntentionActions") {
      text(
        PythonLessonsBundle.message("python.in.place.refactoring.invoke.intentions",
                                    icon(AllIcons.Gutter.SuggestedRefactoringBulb), action(it)))
      triggerAndBorderHighlight().listItem { ui -> // no highlighting
        ui.isToStringContains("'s'")
      }
      proposeRestore {
        checkFirstChange()
      }
      test {
        Thread.sleep(500) // need to check the intention is ready
        actions(it)
      }
    }

    task {
      val prefix = template.indexOf("<name>")
      text(PythonLessonsBundle.message("python.in.place.refactoring.finish.rename", action("EditorChooseLookupItem")))
      restoreByUi(delayMillis = defaultRestoreDelay)
      stateCheck {
        val newName = newName(editor.document.charsSequence, prefix)
        val expected = template.replace("<caret>", "").replace("<name>", newName)
        newName != variableName && editor.document.text == expected
      }
      test { invokeActionViaShortcut("ENTER") }
    }

    waitBeforeContinue(500)

    caret(template.indexOf("stop") + 4)

    lateinit var secondSample: LessonSample

    fun TaskRuntimeContext.checkSecondChange() =
      checkExpectedStateOfEditor(secondSample) { change -> ", start".startsWith(change) }

    prepareRuntimeTask {
      secondSample = prepareSampleFromCurrentState(editor)
    }

    prepareRuntimeTask { // restore point
      setSample(previous.sample)
    }

    task {
      text(PythonLessonsBundle.message("python.in.place.refactoring.add.parameter", code(", start")))
      stateCheck {
        val text = editor.document.text
        val parameter = text.substring(secondSample.startOffset, text.indexOf(')'))
        val parts = parameter.split(" ")
        parts.size == 2 && parts[0] == "," && parts[1].isNotEmpty() && parts[1].all { it.isJavaIdentifierPart() }
      }
      proposeRestore {
        checkSecondChange()
      }
      test { type(", start") }
    }

    lateinit var showIntentionsTaskId: TaskContext.TaskId
    task("ShowIntentionActions") {
      showIntentionsTaskId = taskId
      text(PythonLessonsBundle.message("python.in.place.refactoring.invoke.intention.for.parameter",
                                       icon(AllIcons.Gutter.SuggestedRefactoringBulb), action(it)))
      val updateUsagesText = RefactoringBundle.message("suggested.refactoring.change.signature.intention.text",
                                                       RefactoringBundle.message("suggested.refactoring.usages"))
      triggerAndBorderHighlight().listItem { item ->
        item.isToStringContains(updateUsagesText)
      }
      proposeRestore {
        checkSecondChange()
      }
      test {
        Thread.sleep(500) // need to check the intention is ready
        actions(it)
      }
    }

    task {
      text(PythonLessonsBundle.message("python.in.place.refactoring.update.callers", action("EditorChooseLookupItem")))
      triggerUI().component { ui: JPanel -> // no highlighting
        ui.javaClass.name.contains("ChangeSignaturePopup")
      }
      restoreByUi(delayMillis = defaultRestoreDelay)
      test { invokeActionViaShortcut("ENTER") }
    }

    task {
      text(PythonLessonsBundle.message("python.in.place.refactoring.signature.preview", LessonUtil.rawEnter()))
      triggerUI().component { ui: JLabel -> // no highlighting
        ui.text == RefactoringBundle.message("suggested.refactoring.parameter.values.label.text")
      }
      restoreAfterStateBecomeFalse(restoreId = showIntentionsTaskId) {
        previous.ui?.isShowing != true
      }
      test(waitEditorToBeReady = false) { invokeActionViaShortcut("ENTER") }
    }
    task {
      lateinit var beforeSecondRefactoring: String
      before {
        beforeSecondRefactoring = editor.document.text
      }
      text(PythonLessonsBundle.message("python.in.place.refactoring.set.default.value", code("0"), LessonUtil.rawEnter()))
      restoreByUi(restoreId = showIntentionsTaskId)
      stateCheck {
        editor.document.text != beforeSecondRefactoring && Thread.currentThread().stackTrace.any {
          it.className.contains("PySuggestedRefactoringExecution") && it.methodName == "performChangeSignature"
        }
      }
      test(waitEditorToBeReady = false) {
        type("0")
        invokeActionViaShortcut("ENTER")
      }
    }
    text(PythonLessonsBundle.message("python.in.place.refactoring.remark.about.application.scope"))
  }

  private fun newName(text: CharSequence, prefix: Int): String {
    var i = prefix
    val result = StringBuffer()
    while (i < text.length && text[i].isJavaIdentifierPart()) {
      result.append(text[i])
      i++
    }
    return result.toString()
  }

  override val suitableTips = listOf("InPlaceRefactoring")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(PythonLessonsBundle.message("python.in.place.refactoring.help.rename.link"),
         LessonUtil.getHelpLink("rename-refactorings.html#inplace_rename")),
    Pair(PythonLessonsBundle.message("python.in.place.refactoring.help.signature.link"),
         LessonUtil.getHelpLink("pycharm", "change-signature.html#inplace_change_signature_python")),
  )
}
