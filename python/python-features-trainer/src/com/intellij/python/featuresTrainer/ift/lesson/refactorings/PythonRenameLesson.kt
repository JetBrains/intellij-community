// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.featuresTrainer.ift.lesson.refactorings

import com.intellij.ide.DataManager
import com.intellij.ide.actions.exclusion.ExclusionHandler
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.ui.NameSuggestionsField
import com.intellij.ui.tree.TreeVisitor
import com.intellij.usageView.UsageViewBundle
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.python.featuresTrainer.ift.PythonLessonsBundle
import org.assertj.swing.fixture.JTreeFixture
import training.dsl.*
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.util.isToStringContains
import javax.swing.JButton
import javax.swing.JTree
import javax.swing.tree.TreePath

class PythonRenameLesson : KLesson("Rename", LessonsBundle.message("rename.lesson.name")) {
  override val testScriptProperties = TaskTestContext.TestScriptProperties(10)
  private val template = """
      class Championship:
          def __init__(self):
              self.<name> = 0
      
          def matches_count(self):
              return self.<name> * (self.<name> - 1) / 2
          
          def add_new_team(self):
              self.<name> += 1
      
      def team_matches(champ):
          champ.<name>() - 1

      class Company:
          def __init__(self, t):
              self.teams = t
      
      def company_members(company):
          map(lambda team : team.name, company.teams)

      def teams():
          return 16
      
      c = Championship()
      
      c.<caret><name> = teams()
      
      print(c.<name>)
  """.trimIndent() + '\n'

  private val sample = parseLessonSample(template.replace("<name>", "teams"))

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)
    val dynamicWord = UsageViewBundle.message("usage.view.results.node.dynamic")
    var replace: String? = null
    var dynamicItem: String? = null
    task("RenameElement") {
      text(PythonLessonsBundle.message("python.rename.press.rename", action(it), code("teams"), code("teams_number")))
      triggerUI().component { ui: NameSuggestionsField ->
        ui.addDataChangedListener {
          replace = ui.enteredName
        }
        true
      }
      triggerAndBorderHighlight().treeItem { _: JTree, path: TreePath ->
        val pathStr = path.getPathComponent(1).toString()
        if (path.pathCount == 2 && pathStr.contains(dynamicWord)) {
          dynamicItem = pathStr
          true
        }
        else false
      }
      test {
        actions(it)
        dialog {
          type("teams_number")
          button("Refactor").click()
        }
      }
    }

    task {
      // Increase deterministic: collapse nodes
      before {
        (previous.ui as? JTree)?.let { tree ->
          TreeUtil.collapseAll(tree, 1)
        }
      }
      val dynamicReferencesString = "[$dynamicWord]"
      text(PythonLessonsBundle.message("python.rename.expand.dynamic.references",
                                       code("teams"), strong(dynamicReferencesString)))

      triggerAndBorderHighlight().treeItem { _: JTree, path: TreePath ->
        path.pathCount == 6 && path.getPathComponent(5).isToStringContains("company_members")
      }
      showWarningIfFindToolbarClosed()
      test {
        //TODO: Fix tree access
        val jTree = previous.ui as? JTree ?: return@test
        val di = dynamicItem ?: return@test
        ideFrame {
          val jTreeFixture = JTreeFixture(robot, jTree)
          jTreeFixture.replaceSeparator("@@@")
          jTreeFixture.expandPath(di)
          // WARNING: several exception will be here because of UsageNode#toString inside info output during this operation
        }
      }
    }

    task {
      text(PythonLessonsBundle.message("python.rename.exclude.item", code("company_members"), action("EditorDelete")))

      stateCheck {
        val tree = previous.ui as? JTree ?: return@stateCheck false
        val last = pathToExclude(tree) ?: return@stateCheck false
        val dataContext = DataManager.getInstance().getDataContext(tree)
        val exclusionProcessor: ExclusionHandler<*> = ExclusionHandler.EXCLUSION_HANDLER.getData(dataContext) ?: return@stateCheck false
        val leafToBeExcluded = last.lastPathComponent

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> castHack(processor: ExclusionHandler<T>): Boolean {
          return processor.isNodeExclusionAvailable(leafToBeExcluded as T) && processor.isNodeExcluded(leafToBeExcluded as T)
        }
        castHack(exclusionProcessor)
      }
      showWarningIfFindToolbarClosed()
      test {
        ideFrame {
          type("come")
          invokeActionViaShortcut("DELETE")
        }
      }
    }

    val confirmRefactoringButton = RefactoringBundle.message("usageView.doAction").dropMnemonic()
    task {
      triggerAndBorderHighlight().component { button: JButton ->
        button.text.isToStringContains(confirmRefactoringButton)
      }
    }

    task {
      val result = replace?.let { template.replace("<name>", it).replace("<caret>", "") }
      text(PythonLessonsBundle.message("python.rename.finish.refactoring", strong(confirmRefactoringButton)))
      stateCheck { editor.document.text == result }
      showWarningIfFindToolbarClosed()
      test(waitEditorToBeReady = false) {
        ideFrame {
          button(confirmRefactoringButton).click()
        }
      }
    }
  }

  private fun pathToExclude(tree: JTree): TreePath? {
    return TreeUtil.promiseVisit(tree, TreeVisitor { path ->
      if (path.pathCount == 7 && path.getPathComponent(6).isToStringContains("lambda"))
        TreeVisitor.Action.INTERRUPT
      else
        TreeVisitor.Action.CONTINUE
    }).blockingGet(200)
  }

  private fun TaskContext.showWarningIfFindToolbarClosed() {
    showWarning(PythonLessonsBundle.message("python.rename.find.window.closed.warning", action("ActivateFindToolWindow")),
                restoreTaskWhenResolved = true) {
      previous.ui?.isShowing != true
    }
  }

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("rename.help.link"),
         LessonUtil.getHelpLink("rename-refactorings.html")),
  )
}
