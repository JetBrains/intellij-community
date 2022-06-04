// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.refactorings

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.refactoring.RefactoringBundle
import com.intellij.ui.components.JBList
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.table.JBTableRow
import com.jetbrains.python.PyBundle
import com.jetbrains.python.ift.PythonLessonsBundle
import com.jetbrains.python.inspections.quickfix.PyChangeSignatureQuickFix
import training.dsl.*
import training.dsl.LessonUtil.checkExpectedStateOfEditor
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.util.isToStringContains
import java.util.regex.Pattern
import javax.swing.JDialog
import javax.swing.JTable

class PythonQuickFixesRefactoringLesson
  : KLesson("refactoring.quick.fix", PythonLessonsBundle.message("python.quick.fix.refactoring.lesson.name")) {
  private val sample = parseLessonSample("""
    def foo(x):
        print("Hello ", x)
    
    y = 20
    foo(10<caret>)
    foo(30)
  """.trimIndent())

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)

    val editingFunctionRegex = Regex("""foo\(10,[ ]?y\)""")
    fun checkEditor(editor: Editor) = editor.document.text.contains(editingFunctionRegex)

    task {
      text(PythonLessonsBundle.message("python.quick.fix.refactoring.type.new.argument", code("foo"), code("y"), code(", y")))
      triggerUI().component { _: JBList<*> ->
        checkEditor(editor)
      }
      proposeMyRestore()
      test { type(", y") }
    }

    task {
      text(PythonLessonsBundle.message("python.quick.fix.refactoring.close.completion.list", action("EditorEscape")))
      stateCheck {
        previous.ui?.isShowing != true && checkEditor(editor)
      }
      restoreState {
        !checkEditor(editor)
      }
      test(waitEditorToBeReady = false) { invokeActionViaShortcut("ESCAPE") }
    }

    prepareRuntimeTask { // restore point
      setSample(previous.sample)
    }

    lateinit var showQuickFixesTaskId: TaskContext.TaskId
    task("ShowIntentionActions") {
      showQuickFixesTaskId = taskId
      text(PythonLessonsBundle.message("python.quick.fix.refactoring.invoke.intentions", action(it)))
      triggerAndBorderHighlight().listItem { item ->
        item.isToStringContains("foo(")
      }
      proposeRestore {
        checkExpectedStateOfEditor(previous.sample)
      }
      test {
        Thread.sleep(500) // need to check the intention is ready
        actions(it)
      }
    }
    task {
      text(PythonLessonsBundle.message("python.quick.fix.refactoring.choose.change.signature",
                                       strong(PyBundle.message("QFIX.NAME.change.signature"))))

      triggerAndBorderHighlight().componentPart { table: JTable ->
        val model = table.model
        if (model.rowCount >= 2 && (model.getValueAt(1, 0) as? JBTableRow)?.getValueAt(0) == "y") {
          table.getCellRect(1, 0, true)
        }
        else null
      }
      restoreByUi(delayMillis = defaultRestoreDelay)
      test(waitEditorToBeReady = false) {
        ideFrame {
          jListContains("foo(").clickItem(Pattern.compile(""".*foo\(.*"""))
        }
      }
    }
    task {
      text(PythonLessonsBundle.message("python.quick.fix.refactoring.select.new.parameter",
                                       action("EditorTab"), LessonUtil.rawEnter()))
      triggerAndFullHighlight().withSelector { collection: Collection<EditorComponentImpl> ->
        collection.takeIf { it.size > 2 }?.maxByOrNull { it.locationOnScreen.x }
      }.byComponent { editor: EditorComponentImpl ->
        UIUtil.getParentOfType(JDialog::class.java, editor) != null
      }
      restoreByUi()
      test(waitEditorToBeReady = false) {
        invokeAndWaitIfNeeded(ModalityState.any()) {
          val ui = previous.ui ?: return@invokeAndWaitIfNeeded
          IdeFocusManager.getInstance(project).requestFocus(ui, true)
        }
        invokeActionViaShortcut("ENTER")
      }
    }
    task {
      text(PythonLessonsBundle.message("python.quick.fix.refactoring.set.default.value",
                                       action("EditorTab")))
      restoreByUi()
      stateCheck {
        (previous.ui as? EditorComponentImpl)?.text == "0"
      }
      test(waitEditorToBeReady = false) {
        invokeAndWaitIfNeeded(ModalityState.any()) {
          val ui = previous.ui ?: return@invokeAndWaitIfNeeded
          IdeFocusManager.getInstance(project).requestFocus(ui, true)
        }
        invokeActionViaShortcut("BACK_SPACE")
        type("0")
      }
    }

    task {
      lateinit var beforeRefactoring: String
      before {
        beforeRefactoring = editor.document.text
      }
      text(PythonLessonsBundle.message("python.quick.fix.refactoring.finish.refactoring",
                                       LessonUtil.rawCtrlEnter(), strong(RefactoringBundle.message("refactor.button").dropMnemonic())))

      stateCheck {
        val b = editor.document.text != beforeRefactoring
        b && stackInsideDialogRefactoring()
      }
      restoreAfterStateBecomeFalse(restoreId = showQuickFixesTaskId) {
        !stackInsideDialogRefactoring()
      }

      test(waitEditorToBeReady = false) {
        dialog("Change Signature") {
          button("Refactor").click()
        }
      }
    }
  }

  private fun stackInsideDialogRefactoring(): Boolean {
    return Thread.currentThread().stackTrace.any {
      it.className == PyChangeSignatureQuickFix::class.java.name
    }
  }

  private fun TaskContext.proposeMyRestore() {
    proposeRestore {
      checkExpectedStateOfEditor(sample) { ", y".startsWith(it) || ",y".startsWith(it) }
    }
  }

  override val suitableTips = listOf("QuickFixRightArrow")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(PythonLessonsBundle.message("python.quick.fix.refactoring.help.link"),
         LessonUtil.getHelpLink("change-signature.html")),
    Pair(LessonsBundle.message("context.actions.help.intention.actions"),
         LessonUtil.getHelpLink("intention-actions.html")),
  )
}
