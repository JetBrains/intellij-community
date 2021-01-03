// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.refactorings

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.refactoring.RefactoringBundle
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.impl.button
import com.intellij.testGuiFramework.util.Key
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.table.JBTableRow
import com.jetbrains.python.PyBundle
import com.jetbrains.python.ift.PythonLessonsBundle
import com.jetbrains.python.inspections.quickfix.PyChangeSignatureQuickFix
import training.commands.kotlin.TaskContext
import training.commands.kotlin.TaskTestContext
import training.learn.interfaces.Module
import training.learn.lesson.kimpl.*
import training.learn.lesson.kimpl.LessonUtil.checkExpectedStateOfEditor
import javax.swing.JDialog
import javax.swing.JTable

class PythonQuickFixesRefactoringLesson(module: Module)
  : KLesson("refactoring.quick.fix", PythonLessonsBundle.message("python.quick.fix.refactoring.lesson.name"), module, "Python") {
  private val sample = parseLessonSample("""
    def foo(x):
        print("Hello ", x)
    
    y = 20
    foo(10<caret>)
    foo(30)
  """.trimIndent())

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)
    task {
      text(PythonLessonsBundle.message("python.quick.fix.refactoring.type.new.argument", code("foo"), code("y"), code(", y")))
      stateCheck {
        editor.document.text == StringBuilder(sample.text).insert(sample.startOffset, ", y").toString()
      }
      proposeMyRestore()
      test { type(", y") }
    }

    task {
      text(PythonLessonsBundle.message("python.quick.fix.refactoring.wait.completion.showed"))
      triggerByListItemAndHighlight(highlightBorder = false, highlightInside = false) { item ->
        item.toString().contains("string=y")
      }
      proposeMyRestore()
    }

    task {
      text(PythonLessonsBundle.message("python.quick.fix.refactoring.close.completion.list", action("EditorEscape")))
      stateCheck { previous.ui?.isShowing != true }
      proposeMyRestore()
      test { GuiTestUtil.shortcut(Key.ESCAPE) }
    }

    prepareRuntimeTask { // restore point
      setSample(previous.sample)
    }

    task("ShowIntentionActions") {
      text(PythonLessonsBundle.message("python.quick.fix.refactoring.invoke.intentions", action(it)))
      triggerByListItemAndHighlight(highlightBorder = true, highlightInside = false) { item ->
        item.toString().contains("Change signature of")
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

      triggerByPartOfComponent { table: JTable ->
        val model = table.model
        if (model.rowCount >= 2 && (model.getValueAt(1, 0) as? JBTableRow)?.getValueAt(0) == "y") {
          table.getCellRect(1, 0, true)
        }
        else null
      }
      restoreByUi(500)
      test {
        GuiTestUtil.shortcut(Key.DOWN)
        GuiTestUtil.shortcut(Key.ENTER)
      }
    }
    task {
      text(PythonLessonsBundle.message("python.quick.fix.refactoring.select.new.parameter",
                                 action("EditorTab"), LessonUtil.rawEnter()))

      val selector = { collection: Collection<EditorComponentImpl> ->
        collection.takeIf { it.size > 2 }?.maxByOrNull { it.locationOnScreen.x }
      }
      triggerByUiComponentAndHighlight(selector = selector) { editor: EditorComponentImpl ->
        UIUtil.getParentOfType(JDialog::class.java, editor) != null
      }
      restoreByUi()
      test {
        invokeAndWaitIfNeeded(ModalityState.any()) {
          val ui = previous.ui ?: return@invokeAndWaitIfNeeded
          IdeFocusManager.getInstance(project).requestFocus(ui, true)
        }
        GuiTestUtil.shortcut(Key.ENTER)
      }
    }
    task {
      text(PythonLessonsBundle.message("python.quick.fix.refactoring.set.default.value",
                                 action("EditorTab")))
      restoreByUi()
      stateCheck {
        (previous.ui as? EditorComponentImpl)?.text == "0"
      }
      test {
        invokeAndWaitIfNeeded(ModalityState.any()) {
          val ui = previous.ui ?: return@invokeAndWaitIfNeeded
          IdeFocusManager.getInstance(project).requestFocus(ui, true)
        }
        GuiTestUtil.shortcut(Key.BACK_SPACE)
        type("0")
        //ideFrame {
        //  previous.ui?.let { usagesTab -> jComponent(usagesTab).click() }
        //}
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
      restoreState(delayMillis = 500) {
        !stackInsideDialogRefactoring()
      }

      test {
        with(TaskTestContext.guiTestCase) {
          dialog("Change Signature") {
            button("Refactor").click()
          }
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
      checkExpectedStateOfEditor(sample) { ", y".startsWith(it) }
    }
  }
}
