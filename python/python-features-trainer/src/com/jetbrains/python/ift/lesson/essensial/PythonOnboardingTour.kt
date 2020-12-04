// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.essensial

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.RunManager
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.openapi.wm.impl.StripeButton
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.Alarm
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PythonLanguage
import training.commands.kotlin.TaskRuntimeContext
import training.learn.LessonsBundle
import training.learn.interfaces.Module
import training.learn.lesson.general.run.toggleBreakpointTask
import training.learn.lesson.kimpl.*
import training.ui.LearningUiHighlightingManager
import java.awt.Component
import java.awt.Dimension
import java.awt.event.KeyEvent
import javax.swing.JTree
import javax.swing.tree.TreePath

@Suppress("HardCodedStringLiteral")
class PythonOnboardingTour(module: Module) :
  KLesson("python.onboarding", "Onboarding tour", module, PythonLanguage.INSTANCE.id) {

  private val demoConfigurationName: String = "welcome"
  private val demoFileName: String = "$demoConfigurationName.py"

  override val properties = LessonProperties(
    canStartInDumbMode = true,
    showLearnToolwindowAtStart = false,
    openFileAtStart = false
  )

  val sample: LessonSample = parseLessonSample("""
    def find_average(values: list):
        result = 0
        for v in values:
            result += v
        <caret>return result<caret id=2/>


    print("AVERAGE", find_average([5,6, 7, 8]))
  """.trimIndent())

  override val lessonContent: LessonContext.() -> Unit = {
    prepareRuntimeTask {
      configurations().forEach { runManager().removeConfiguration(it) }

      val root = ProjectRootManager.getInstance(project).contentRoots[0]
      if (root.findChild(demoFileName) == null) invokeLater {
        runWriteAction {
          root.createChildData(this, demoFileName)
        }
      }
    }
    task {
      triggerByUiComponentAndHighlight { progress: NonOpaquePanel ->
        progress.javaClass.name.contains("InlineProgressPanel")
      }
    }

    gotItTask(Balloon.Position.above, Dimension(500, 200)) {
      "When you open a project first time <ide/> needs some time to <strong>index</strong> Python SDK and the project itself: " +
      "IDE is scanning the project and libraries to ensure prompt (quick?) performance of code completion, finding " +
      "usages, navigation and so on. Note that many IDE operations and capabilities are restricted or unavailable during " +
      "<strong>indexing</strong> process."
    }

    projectTasks()

    prepareSample(sample)

    openLearnToolwindow()

    runTasks()

    debugTaks()

    completionSteps()

    contextActions()

    searchEverywhereTasks()

    task {
      val veryFullName = "${PluginManagerCore.VENDOR_JETBRAINS} ${ApplicationNamesInfo.getInstance().fullProductNameWithEdition}"
      text("Here we will finish our onboarding tour. In this tool window you can find other interactive guides about different features in <ide/>. " +
           "Most of them are much shorter :) Thank you for choosing ${strong(veryFullName)}.")

      text("<strong>Note,</strong> that currently opened project is the <strong>demo learning project</strong> used by this tool. Any modifications here are " +
           "likely to be rewritten automatically at some point of time. So we recommend you to close this project when you finish with IDE learning and " +
           "open/create your own project to do your work/experiments with Python.")
    }

    task {
      text("Last task")
      stateCheck { false }
    }
  }

  private fun LessonContext.debugTaks() {
    var logicalPosition = LogicalPosition(0, 0)
    prepareRuntimeTask {
      logicalPosition = editor.offsetToLogicalPosition(sample.startOffset)
    }
    caret(sample.startOffset)

    toggleBreakpointTask(sample, { logicalPosition }, checkLine = false) {
      "You may notice that method ${code("find_average")} returns wrong answer. Let's debug it and stop at the" +
      "${code("return")} statement. First of all, set breakpoint: just click at the gutter in the highlighted area."
    }

    highlightButtonById("Debug")

    actionTask("Debug") {
      "Let's start debug. Click at ${icon(AllIcons.Actions.StartDebugger)} icon."
    }

    task {
      val needFirstAction = ActionManager.getInstance().getAction("ShowExecutionPoint")
      triggerByUiComponentAndHighlight(highlightInside = false) { ui: ActionToolbarImpl ->
        ui.size.let { it.width > 0 && it.height > 0 } && ui.place == "DebuggerToolbar" && checkFirstButton(ui, needFirstAction)
      }
    }

    highlightAllFoundUi(clearPreviousHighlights = false, highlightInside = false) { ui: ActionToolbarImpl ->
      ui.size.let { it.width > 0 && it.height > 0 } && ui.place == "DebuggerToolbar" &&
      checkFirstButton(ui, ActionManager.getInstance().getAction("Rerun"))
    }

    gotItTask(Balloon.Position.above, Dimension(500, 150)) {
      "In the debug tool window you may find the most of the actions you can do during debug like step in, step over, " +
      "run to cursor and so on. ${strong(LessonsBundle.message("debug.workflow.lesson.name"))} lesson can give you overview of them. " +
      "We suggest to pass it later!"
    }

    highlightButtonById("Stop")
    actionTask("Stop") {
      "Let's stop debugging. Click at ${icon(AllIcons.Actions.Suspend)} icon."
    }

    prepareRuntimeTask {
      LearningUiHighlightingManager.clearHighlights()
    }
  }

  private fun checkFirstButton(ui: ActionToolbarImpl,
                               needFirstAction: AnAction?): Boolean {
    return ui.components.let {
      it.isNotEmpty<Component?>() && (it[0] as? ActionButton)?.let { first ->
        first.action == needFirstAction
      } == true
    }
  }

  private fun LessonContext.runTasks() {
    val runItem = ExecutionBundle.message("default.runner.start.action.text").dropMnemonic() + " '$demoConfigurationName'"

    task {
      text("Let's run this sample. Right click at the free space somewhere in the editor to invoke the context menu.")
      triggerByUiComponentAndHighlight(highlightInside = false) { ui: ActionMenuItem ->
        ui.text?.contains(runItem) ?: false
      }
    }
    task {
      text("And choose ${strong(runItem)}. You may see it has ${action("RunClass")} shortcut. " +
           "You can use it instead of this menu.")
      toolWindowShowed("Run")
      stateCheck {
        configurations().isNotEmpty()
      }
    }

    task {
      triggerByUiComponentAndHighlight(highlightInside = false) { ui: ActionToolbarImpl ->
        ui.place == "NavBarToolbar"
      }
    }

    gotItTask(Balloon.Position.below, Dimension(500, 170)) {
      "You may notice that <ide/> has been created the temporary run configuration. With this toolbar you may choose " +
      "run configuration, modify it (drop-down menu), rerun it, debug, collect code coverage, profile and so on. " +
      "Later you can open ${strong(LessonsBundle.message("run.configuration.lesson.name"))} lesson to find more information."
    }
  }

  private fun LessonContext.openLearnToolwindow() {
    task {
      triggerByUiComponentAndHighlight { stripe: StripeButton ->
        (stripe !is SquareStripeButton) && stripe.windowInfo.id == "Learn"
      }
    }

    task {
      text("Let's switch to the Learn tool window and continue this lesson.",
           LearningBalloonConfig(Balloon.Position.atRight, dimension = Dimension(300, 100)))
      stateCheck {
        ToolWindowManager.getInstance(project).getToolWindow("Learn")?.isVisible == true
      }
    }

    prepareRuntimeTask {
      LearningUiHighlightingManager.clearHighlights()
    }
  }

  private fun LessonContext.projectTasks() {
    prepareRuntimeTask {
      LessonUtil.hideStandardToolwindows(project)
    }
    task {
      triggerByUiComponentAndHighlight { stripe: StripeButton ->
        (stripe !is SquareStripeButton) && stripe.windowInfo.id == "Project"
      }
    }

    task {
      var collapsed = false

      text("One of the main tool windows is Project View toolwindow. Let's click at its <strong>stripe button</strong> to open it " +
           "and look at our simple demo project! Or you can open it by ${action("ActivateProjectToolWindow")} shortcut.",
           LearningBalloonConfig(Balloon.Position.atRight, Dimension(500, 120)))
      triggerByFoundPathAndHighlight { tree: JTree, path: TreePath ->
        val result = path.pathCount >= 1 && path.getPathComponent(0).toString().contains("PyCharmLearningProject")
        if (result) {
          if (!collapsed) {
            invokeLater {
              Alarm().addRequest({ tree.collapsePath(path) }, 300)
            }
          }
          collapsed = true
        }
        result
      }
    }

    // Why it breaks `previous` work
    waitBeforeContinue(500)

    task {
      text("Here you can see several top-level items: the project directory itself, external library (from the configured SDK and so on) " +
           "and some other auxiliary staff. Now let's expand the project directory item.",
           LearningBalloonConfig(Balloon.Position.atRight, Dimension(500, 150)))
      triggerByFoundPathAndHighlight { _: JTree, path: TreePath ->
        path.pathCount >= 3 && path.getPathComponent(2).toString().contains(demoFileName)
      }
    }

    task {
      text("Let's open file ${code(demoFileName)}.",
           LearningBalloonConfig(Balloon.Position.atRight, dimension = Dimension(300, 100)))
      stateCheck l@{
        if (FileEditorManager.getInstance(project).selectedTextEditor == null) return@l false
        virtualFile.name == demoFileName
      }
    }
  }

  private fun LessonContext.completionSteps() {
    val completionPosition = sample.getPosition(2)
    caret(completionPosition)
    task {
      text("It seems we need to divide the ${code("result")} sum by ${code("values")} length. " +
           "Type ${code(" / le")}")
      triggerByListItemAndHighlight(highlightBorder = true, highlightInside = false) { // no highlighting
        it.toString().contains("string=len;")
      }
    }

    task("EditorChooseLookupItem") {
      text("<ide/> shows completion variants automatically as you type. " +
           "Select ${code("len(__obj)")} (by keyboard arrows) item and press ${LessonUtil.rawEnter()}.")
      trigger(it) {
        checkEditorModification(completionPosition, "/len()")
      }
    }

    task("CodeCompletion") {
      text("The caret is inside parenthesis ${code("()")} now. Let's invoke completion list explicitly by ${action(it)}")
      trigger(it)
      triggerByListItemAndHighlight(highlightBorder = true, highlightInside = false) { item ->
        item.toString().contains("values")
      }
    }

    task("EditorChooseLookupItem") {
      text("Apply ${strong("values")} item. You can start to type the variable to reduce completion list.")
      trigger(it) {
        checkEditorModification(completionPosition, "/len(values)")
      }
    }
  }

  private fun TaskRuntimeContext.checkEditorModification(completionPosition: LessonSamplePosition, needChange: String): Boolean {
    val startOfChange = completionPosition.startOffset
    val sampleText = sample.text
    val prefix = sampleText.substring(0, startOfChange)
    val suffix = sampleText.substring(startOfChange, sampleText.length)
    val current = editor.document.text

    if (!current.startsWith(prefix)) return false
    if (!current.endsWith(suffix)) return false

    val indexOfSuffix = current.indexOf(suffix)
    if (indexOfSuffix < startOfChange) return false

    val change = current.substring(startOfChange, indexOfSuffix)

    return change.replace(" ", "") == needChange
  }

  private fun LessonContext.contextActions() {
    val reformatMessage = PyBundle.message("QFIX.reformat.file")
    caret(",6")
    task("ShowIntentionActions") {
      text("We moved the caret at the warning. In many cases <ide/> can guess how to fix warnings, syntax errors or guess your " +
           "<strong>intention</strong>. So let's invoke one of the most useful actions, ${LessonUtil.actionName(it)}. Press ${action(it)}.")
      triggerByListItemAndHighlight(highlightBorder = true, highlightInside = false) { item ->
        item.toString().contains(reformatMessage)
      }
    }

    var before = ""
    prepareRuntimeTask {
      before = editor.document.text
    }

    task {
      text("Let's apply the first item, quick fix for this problem: ${strong(reformatMessage)}.")
      stateCheck {
        // TODO: make normal check
        before != editor.document.text
      }
    }

    val returnTypeMessage = PyPsiBundle.message("INTN.specify.return.type.in.annotation")
    caret("find_average")
    task("ShowIntentionActions") {
      text("<ide/> knows a lot of intentions. During your everyday work try to invoke ${LessonUtil.actionName(it)} every time you " +
           "think there might be a good solution or intention. You will save a lot of time and make coding process to be much more fun! " +
           "Now let's look what can be applied for ${code("find_average")} method. Press ${action(it)} again.")
      triggerByListItemAndHighlight(highlightBorder = true, highlightInside = false) { item ->
        item.toString().contains(returnTypeMessage)
      }
    }

    prepareRuntimeTask {
      before = editor.document.text
    }

    task {
      text("Let's apply ${strong(returnTypeMessage)} intention.")
      stateCheck {
        // TODO: make normal check
        before != editor.document.text
      }
    }

    task {
      text("Note that the caret has been moved to the place for return type. Type ${
        code("float")
      } now and then press ${LessonUtil.rawEnter()}.")
      stateCheck {
        // TODO: make normal check
        editor.document.text.contains("float")
      }
    }
  }

  private fun LessonContext.searchEverywhereTasks() {
    caret("AVERAGE", select = true)
    task("SearchEverywhere") {
      text("You may notice we selected ${code("AVERAGE")}. Let's look at another important <ide/> feature: " +
           "${LessonUtil.actionName(it)}. Press ${LessonUtil.rawKeyStroke(KeyEvent.VK_SHIFT)} two times in a row.")
      trigger(it)
    }

    task {
      text("Here you can find any entity in your project or any feature in <ide/> by its name! As you see, selected text automatically " +
           "copied into input string. And the only item we found by now is the ${code("find_average")} function from current file. " +
           "Later you may pass ${strong(LessonsBundle.message("search.everywhere.lesson.name"))} lesson to learn " +
           "more about code navigation and library staff discover.")
      text("Now let's clear the ${LessonUtil.actionName("SearchEverywhere")} input field.")
      stateCheck { checkWordInSearch("") }
    }

    val toggleCase = ActionsBundle.message("action.EditorToggleCase.text")
    task {
      text("Suppose we want to make ${code("AVERAGE")} string to be lower case. Let's look for the corresponding action. " +
           "Type ${strong("case")} into this search string.")
      triggerByListItemAndHighlight { item ->
        item.toString().contains(toggleCase)
      }
    }

    var before = ""
    prepareRuntimeTask {
      before = editor.document.text
    }
    task {
      text("We found ${strong(toggleCase)} action. Note that it has its own shortcut and you can remember and use it later. " +
           "But it may be needed rarely so you can just find it again when you will need it. Now apply the action.")
      stateCheck {
        // TODO: make normal check
        before != editor.document.text
      }
    }

    task {
      text("You may want to look for some <ide/> feature without any interference with you own or library code entities. " +
           "You can pass ${strong(LessonsBundle.message("goto.action.lesson.name"))} lesson to learn how to do it and more.")
    }
  }

  private fun TaskRuntimeContext.checkWordInSearch(expected: String): Boolean =
    (focusOwner as? ExtendableTextField)?.text.equals(expected, ignoreCase = true)

  private fun TaskRuntimeContext.runManager() = RunManager.getInstance(project)
  private fun TaskRuntimeContext.configurations() =
    runManager().allSettings.filter { it.name.contains(demoConfigurationName) }
}
