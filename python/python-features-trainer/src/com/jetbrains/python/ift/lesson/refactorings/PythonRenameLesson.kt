// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.refactorings

import com.intellij.ide.DataManager
import com.intellij.ide.actions.exclusion.ExclusionHandler
import com.intellij.openapi.application.runReadAction
import com.intellij.refactoring.RefactoringBundle
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.button
import com.intellij.testGuiFramework.impl.jTree
import com.intellij.testGuiFramework.util.Key
import com.intellij.ui.tree.TreeVisitor
import com.intellij.usageView.UsageViewBundle
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.python.ift.PythonLessonsBundle
import org.jetbrains.annotations.Nullable
import training.commands.kotlin.TaskTestContext
import training.learn.LessonsBundle
import training.learn.interfaces.Module
import training.learn.lesson.kimpl.KLesson
import training.learn.lesson.kimpl.LessonContext
import training.learn.lesson.kimpl.dropMnemonic
import training.learn.lesson.kimpl.parseLessonSample
import java.util.regex.Pattern
import javax.swing.JButton
import javax.swing.JTree
import javax.swing.tree.TreePath

class PythonRenameLesson(module: Module)
  : KLesson("Rename", LessonsBundle.message("rename.lesson.name"), module, "Python") {
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

  /** For test only */
  private val substringPredicate: (String, String) -> Boolean = { found: String, wanted: String -> found.contains(wanted) }

  private val sample = parseLessonSample(template.replace("<name>", "teams"))

  private val replacePreviewPattern = Pattern.compile(".*Variable to be renamed to (\\w+).*")

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)
    var replace: String? = null
    task("RenameElement") {
      text(PythonLessonsBundle.message("python.rename.press.rename", action(it), code("teams"), code("teams_number")))
      triggerByFoundPathAndHighlight { tree: JTree, path: TreePath ->
        if (path.pathCount == 2 && path.getPathComponent(1).toString().contains("Dynamic")) {
          replace = replacePreviewPattern.matcher(tree.model.root.toString()).takeIf { m -> m.find() }?.group(1)
          true
        }
        else false
      }
      test {
        actions(it)
        with(TaskTestContext.guiTestCase) {
          dialog {
            typeText("teams_number")
            button("Refactor").click()
          }
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
      val dynamicReferencesString = "[${UsageViewBundle.message("usage.view.results.node.dynamic")}]"
      text(PythonLessonsBundle.message("python.rename.expand.dynamic.references",
                                 code("teams"), strong(dynamicReferencesString)))

      triggerByFoundPathAndHighlight { _: JTree, path: TreePath ->
        path.pathCount == 6 && path.getPathComponent(5).toString().contains("company_members")
      }
      test {
        ideFrame {
          val jTree = runReadAction {
            jTree(dynamicReferencesString, timeout = Timeouts.seconds03, predicate = substringPredicate)
          }
          // WARNING: several exception will be here because of UsageNode#toString inside info output during this operation
          jTree.doubleClickPath()
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
        fun <T : Any?> castHack(processor: ExclusionHandler<T>): Boolean {
          return processor.isNodeExclusionAvailable(leafToBeExcluded as T) && processor.isNodeExcluded(leafToBeExcluded as T)
        }
        castHack(exclusionProcessor)
      }
      test {
        ideFrame {
          type("co_me")
          GuiTestUtil.shortcut(Key.DELETE)
        }
      }
    }

    val confirmRefactoringButton = RefactoringBundle.message("usageView.doAction").dropMnemonic()
    task {
      triggerByUiComponentAndHighlight(highlightInside = false) { button: JButton ->
        button.text == confirmRefactoringButton
      }
    }

    task {
      val result = replace?.let { template.replace("<name>", it).replace("<caret>", "") }
      text(PythonLessonsBundle.message("python.rename.finish.refactoring", strong(confirmRefactoringButton)))
      stateCheck { editor.document.text == result }
      test {
        ideFrame {
          button(confirmRefactoringButton).click()
        }
      }
    }
  }

  private fun pathToExclude(tree: JTree): @Nullable TreePath? {
    return TreeUtil.promiseVisit(tree, TreeVisitor { path ->
      if (path.pathCount == 7 && path.getPathComponent(6).toString().contains("lambda"))
        TreeVisitor.Action.INTERRUPT
      else
        TreeVisitor.Action.CONTINUE
    }).blockingGet(200)
  }
}
