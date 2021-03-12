// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.essensial

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.RunManager
import com.intellij.execution.ui.layout.impl.JBRunnerTabs
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.util.gotoByName.GotoActionItemProvider
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.FocusManagerImpl
import com.intellij.openapi.wm.impl.StripeButton
import com.intellij.ui.UIBundle
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.xdebugger.XDebuggerBundle
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.ift.PythonLessonsBundle
import icons.FeaturesTrainerIcons
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nls
import training.dsl.*
import training.dsl.LessonUtil.checkExpectedStateOfEditor
import training.dsl.LessonUtil.restoreIfModified
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.learn.LearnBundle
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.learn.course.Lesson
import training.learn.course.LessonProperties
import training.learn.lesson.LessonListener
import training.learn.lesson.LessonManager
import training.learn.lesson.general.run.toggleBreakpointTask
import training.ui.LearningUiHighlightingManager
import training.ui.LearningUiManager
import training.util.invokeActionForFocusContext
import java.awt.Component
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.TreePath

class PythonOnboardingTour :
  KLesson("python.onboarding", PythonLessonsBundle.message("python.onboarding.lesson.name")) {

  private val demoConfigurationName: String = "welcome"
  private val demoFileName: String = "$demoConfigurationName.py"

  override val properties = LessonProperties(
    canStartInDumbMode = true,
    openFileAtStart = false
  )

  override val testScriptProperties = TaskTestContext.TestScriptProperties(skipTesting = true)

  val sample: LessonSample = parseLessonSample("""
    def find_average(values: list)<caret id=3/>:
        result = 0
        for v in values:
            result += v
        <caret>return result<caret id=2/>


    print("AVERAGE", find_average([5,6, 7, 8]))
  """.trimIndent())

  override val lessonContent: LessonContext.() -> Unit = {
    prepareRuntimeTask {
      addEndLessonListener()
      configurations().forEach { runManager().removeConfiguration(it) }

      val root = ProjectRootManager.getInstance(project).contentRoots[0]
      if (root.findChild(demoFileName) == null) invokeLater {
        runWriteAction {
          root.createChildData(this, demoFileName)
        }
      }
    }

    projectTasks()

    prepareSample(sample)

    openLearnToolwindow()

    runTasks()

    debugTasks()

    completionSteps()

    contextActions()

    waitBeforeContinue(500)

    searchEverywhereTasks()

    task {
      text(PythonLessonsBundle.message("python.onboarding.epilog",
                                       getCallBackActionId("CloseProject"),
                                       returnToWelcomeScreenRemark(),
                                       LearningUiManager.addCallback { LearningUiManager.resetModulesView() }))
    }
  }

  private fun returnToWelcomeScreenRemark(): String {
    val isSingleProject = ProjectManager.getInstance().openProjects.size == 1
    return if (isSingleProject) PythonLessonsBundle.message("python.onboarding.return.to.welcome") else ""
  }

  private fun TaskRuntimeContext.addEndLessonListener() {
    val listener = object  : LessonListener {
      override fun lessonPassed(lesson: Lesson) {
        invokeLater {
          val result = MessageDialogBuilder.yesNoCancel(PythonLessonsBundle.message("python.onboarding.finish.title"),
                                                        PythonLessonsBundle.message("python.onboarding.finish.text", returnToWelcomeScreenRemark()))
            .yesText(PythonLessonsBundle.message("python.onboarding.finish.exit"))
            .noText(PythonLessonsBundle.message("python.onboarding.finish.modules"))
            .icon(FeaturesTrainerIcons.Img.PluginIcon)
            .show(project)

          when (result) {
            Messages.YES -> invokeLater {
              LessonManager.instance.stopLesson()
              val closeAction = ActionManager.getInstance().getAction("CloseProject") ?: error("No close project action found")
              invokeActionForFocusContext(closeAction)
            }
            Messages.NO -> invokeLater {
              LearningUiManager.resetModulesView()
            }
          }
        }
      }

      override fun lessonStopped(lesson: Lesson) {
        invokeLater {
          removeLessonListener(this)
        }
      }
    }
    addLessonListener(listener)
  }

  private fun getCallBackActionId(@Suppress("SameParameterValue") actionId: String): Int {
    val action = ActionManager.getInstance().getAction(actionId) ?: error("No action with Id $actionId")
    return LearningUiManager.addCallback { invokeActionForFocusContext(action) }
  }

  private fun LessonContext.debugTasks() {
    var logicalPosition = LogicalPosition(0, 0)
    prepareRuntimeTask {
      logicalPosition = editor.offsetToLogicalPosition(sample.startOffset)
    }
    caret(sample.startOffset)

    toggleBreakpointTask(sample, { logicalPosition }, checkLine = false) {
      text(PythonLessonsBundle.message("python.onboarding.balloon.click.here"),
           LearningBalloonConfig(Balloon.Position.below, width = 0, duplicateMessage = false))
      text(PythonLessonsBundle.message("python.onboarding.toggle.breakpoint.1", code("find_average")))
      text(PythonLessonsBundle.message("python.onboarding.toggle.breakpoint.2"))
    }

    highlightButtonByIdTask("Debug")

    actionTask("Debug") {
      buttonBalloon(PythonLessonsBundle.message("python.onboarding.balloon.start.debugging"))
      restoreIfModified(sample)
      PythonLessonsBundle.message("python.onboarding.start.debugging", icon(AllIcons.Actions.StartDebugger))
    }

    task {
      // Need to wait until Debugger tab will be selected
      stateCheck {
        val f = UIUtil.getParentOfType(JBRunnerTabs::class.java, focusOwner)
        f?.selectedInfo?.text == XDebuggerBundle.message("xdebugger.debugger.tab.title")
      }
    }

    task {
      val needFirstAction = ActionManager.getInstance().getAction("ShowExecutionPoint")
      triggerByUiComponentAndHighlight(highlightInside = true, usePulsation = true) { ui: ActionToolbarImpl ->
        ui.size.let { it.width > 0 && it.height > 0 } && ui.place == "DebuggerToolbar" && checkFirstButton(ui, needFirstAction)
      }
    }

    highlightAllFoundUi(clearPreviousHighlights = false, highlightInside = true, usePulsation = true) { ui: ActionToolbarImpl ->
      ui.size.let { it.width > 0 && it.height > 0 } && ui.place == "DebuggerToolbar" &&
      checkFirstButton(ui, ActionManager.getInstance().getAction("Rerun"))
    }

    task {
      text(PythonLessonsBundle.message("python.onboarding.balloon.about.debug.panel",
                                       strong(UIBundle.message("tool.window.name.debug")),
                                       strong(LessonsBundle.message("debug.workflow.lesson.name"))))
      proceedLink()
      restoreIfModified(sample)
    }

    highlightButtonByIdTask("Stop")
    actionTask("Stop") {
      buttonBalloon(
        PythonLessonsBundle.message("python.onboarding.balloon.stop.debugging")) { list -> list.minByOrNull { it.locationOnScreen.y } }
      restoreIfModified(sample)
      PythonLessonsBundle.message("python.onboarding.stop.debugging", icon(AllIcons.Actions.Suspend))
    }

    prepareRuntimeTask {
      LearningUiHighlightingManager.clearHighlights()
    }
  }

  private fun LessonContext.highlightButtonByIdTask(actionId: String) {
    val highlighted = highlightButtonById(actionId)
    task {
      addStep(highlighted)
    }
  }

  private fun TaskContext.buttonBalloon(@Language("HTML") @Nls message: String,
                                        chooser: (List<JComponent>) -> JComponent? = { it.firstOrNull() }) {
    val highlightingComponent = chooser(LearningUiHighlightingManager.highlightingComponents.filterIsInstance<JComponent>())
    val useBalloon = LearningBalloonConfig(Balloon.Position.below,
                                           width = 0,
                                           highlightingComponent = highlightingComponent,
                                           duplicateMessage = false)
    text(message, useBalloon)
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
      text(PythonLessonsBundle.message("python.onboarding.context.menu"))
      triggerByUiComponentAndHighlight(usePulsation = true) { ui: ActionMenuItem ->
        ui.text?.contains(runItem) ?: false
      }
      restoreIfModified(sample)
    }
    task {
      text(PythonLessonsBundle.message("python.onboarding.run.sample", strong(runItem), action("RunClass")))
      checkToolWindowState("Run", true)
      stateCheck {
        configurations().isNotEmpty()
      }
      restoreIfModified(sample)
    }

    task {

      triggerByPartOfComponent(highlightInside = true, usePulsation = true) { ui: ActionToolbarImpl ->
        ui.takeIf { (ui.place == "NavBarToolbar" || ui.place == "MainToolbar") }?.let { toolbar ->
          val configurations = ui.components.find { it is JPanel && it.components.any { b -> b is ComboBoxAction.ComboBoxButton } }
          val stop = ui.components.find { it is ActionButton && it.action == ActionManager.getInstance().getAction("Stop") }
          if (configurations != null && stop != null) {
            val x = configurations.x
            val y = configurations.y
            val width = stop.x + stop.width - x
            val height = stop.y + stop.height - y
            Rectangle(x, y, width, height)
          } else null
        }
      }
    }

    task {
      text(PythonLessonsBundle.message("python.onboarding.temporary.configuration.description",
                                       icon(AllIcons.Actions.Execute),
                                       icon(AllIcons.Actions.StartDebugger),
                                       icon(AllIcons.Actions.Profile),
                                       icon(AllIcons.General.RunWithCoverage)))
      proceedLink()
      restoreIfModified(sample)
    }
  }

  private fun TaskContext.proceedLink() {
    val gotIt = CompletableFuture<Boolean>()
    runtimeText {
      removeAfterDone = true
      PythonLessonsBundle.message("python.onboarding.proceed.to.the.next.step", LearningUiManager.addCallback { gotIt.complete(true) })
    }
    addStep(gotIt)
  }

  private fun LessonContext.openLearnToolwindow() {
    task {
      triggerByUiComponentAndHighlight(usePulsation = true) { stripe: StripeButton ->
        stripe.windowInfo.id == "Learn"
      }
    }

    task {
      text(
        PythonLessonsBundle.message("python.onboarding.balloon.open.learn.toolbar", strong(LearnBundle.message("toolwindow.stripe.Learn"))),
           LearningBalloonConfig(Balloon.Position.atRight, width = 300))
      stateCheck {
        ToolWindowManager.getInstance(project).getToolWindow("Learn")?.isVisible == true
      }
      restoreIfModified(sample)
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
      triggerByUiComponentAndHighlight(usePulsation = true) { stripe: StripeButton ->
        stripe.windowInfo.id == "Project"
      }
    }

    task {
      var collapsed = false

      text(PythonLessonsBundle.message("python.onboarding.project.view.description",
                                       action("ActivateProjectToolWindow")))
      text(PythonLessonsBundle.message("python.onboarding.balloon.project.view"),
           LearningBalloonConfig(Balloon.Position.atRight, width = 0))
      triggerByFoundPathAndHighlight { tree: JTree, path: TreePath ->
        val result = path.pathCount >= 1 && path.getPathComponent(0).toString().contains("PyCharmLearningProject")
        if (result) {
          if (!collapsed) {
            invokeLater {
              tree.collapsePath(path)
            }
          }
          collapsed = true
        }
        result
      }
    }

    fun isDemoFilePath(path: TreePath) =
      path.pathCount >= 3 && path.getPathComponent(2).toString().contains(demoFileName)

    task {
      text(PythonLessonsBundle.message("python.onboarding.balloon.project.directory"),
           LearningBalloonConfig(Balloon.Position.atRight, duplicateMessage = true, width = 400))
      triggerByFoundPathAndHighlight { _: JTree, path: TreePath ->
        isDemoFilePath(path)
      }
      restoreByUi()
    }

    task {
      text(PythonLessonsBundle.message("python.onboarding.balloon.open.file", code(demoFileName)),
           LearningBalloonConfig(Balloon.Position.atRight, duplicateMessage = true, width = 0))
      stateCheck l@{
        if (FileEditorManager.getInstance(project).selectedTextEditor == null) return@l false
        virtualFile.name == demoFileName
      }
      restoreState {
        (previous.ui as? JTree)?.takeIf { tree ->
          TreeUtil.visitVisibleRows(tree, TreeVisitor { path ->
            if (isDemoFilePath(path)) TreeVisitor.Action.INTERRUPT else TreeVisitor.Action.CONTINUE
          }) != null
        }?.isShowing?.not() ?: true
      }
    }
  }

  private fun LessonContext.completionSteps() {
    val completionPosition = sample.getPosition(2)
    caret(completionPosition)
    prepareRuntimeTask {
      FocusManagerImpl.getInstance(project).requestFocusInProject(editor.contentComponent, project)
    }
    task {
      text(PythonLessonsBundle.message("python.onboarding.type.division", code(" / l")))
      var wasEmpty = false
      proposeRestore {
        checkExpectedStateOfEditor(previous.sample) {
          if (it.isEmpty()) wasEmpty = true
          wasEmpty && "/len".contains(it.replace(" ", ""))
        }
      }
      triggerByListItemAndHighlight(highlightBorder = true, highlightInside = false) { // no highlighting
        it.toString().contains("string=len;")
      }
    }

    task("EditorChooseLookupItem") {
      text(PythonLessonsBundle.message("python.onboarding.choose.len.item",
                                       code("len(__obj)"), LessonUtil.rawEnter()))
      trigger(it) {
        checkEditorModification(completionPosition, "/len()")
      }
      restoreByUi()
    }

    task("CodeCompletion") {
      text(PythonLessonsBundle.message("python.onboarding.invoke.completion",
                                       code("values"),
                                       code("()"),
                                       action(it)))
      trigger(it)
      triggerByListItemAndHighlight(highlightBorder = true, highlightInside = false) { item ->
        item.toString().contains("values")
      }
      restoreIfModifiedOrMoved()
    }

    task("EditorChooseLookupItem") {
      text(PythonLessonsBundle.message("python.onboarding.choose.values.item",
                                       strong("val"), code("values"), LessonUtil.rawEnter()))
      trigger(it) {
        checkEditorModification(completionPosition, "/len(values)")
      }
      restoreByUi()
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
      text(PythonLessonsBundle.message("python.onboarding.invoke.intention.for.warning.1"))
      text(PythonLessonsBundle.message("python.onboarding.invoke.intention.for.warning.2", action(it)))
      triggerByListItemAndHighlight(highlightBorder = true, highlightInside = false) { item ->
        item.toString().contains(reformatMessage)
      }
      restoreIfModifiedOrMoved()
    }

    task {
      text(PythonLessonsBundle.message("python.onboarding.select.fix", strong(reformatMessage)))
      stateCheck {
        // TODO: make normal check
        previous.sample.text != editor.document.text
      }
      restoreByUi(delayMillis = defaultRestoreDelay)
    }

    val returnTypeMessage = PyPsiBundle.message("INTN.specify.return.type.in.annotation")
    caret("find_average")
    task("ShowIntentionActions") {
      text(PythonLessonsBundle.message("python.onboarding.invoke.intention.for.code",
                                       code("find_average"), action(it)))
      triggerByListItemAndHighlight(highlightBorder = true, highlightInside = false) { item ->
        item.toString().contains(returnTypeMessage)
      }
      restoreIfModifiedOrMoved()
    }

    task {
      text(PythonLessonsBundle.message("python.onboarding.apply.intention", strong(returnTypeMessage), LessonUtil.rawEnter()))
      stateCheck {
        // TODO: make normal check
        previous.sample.text != editor.document.text
      }
      restoreByUi(delayMillis = defaultRestoreDelay)
    }

    task {
      lateinit var forRestore: LessonSample
      before {
        val text = previous.sample.text
        val toReplace = "object"
        forRestore = LessonSample(text.replace(toReplace, ""), text.indexOf(toReplace).takeIf { it != -1 } ?: 0)
      }
      text(PythonLessonsBundle.message("python.onboarding.complete.template", code("float"), LessonUtil.rawEnter()))
      stateCheck {
        // TODO: make normal check
        val activeTemplate = TemplateManagerImpl.getInstance(project).getActiveTemplate(editor)
        editor.document.text.contains("float") && activeTemplate == null
      }
      proposeRestore {
        checkExpectedStateOfEditor(forRestore) {
          "object".contains(it) || "float".contains(it)
        }
      }
    }
  }

  private fun LessonContext.searchEverywhereTasks() {
    val toggleCase = ActionsBundle.message("action.EditorToggleCase.text")
    caret("AVERAGE", select = true)
    task("SearchEverywhere") {
      text(PythonLessonsBundle.message("python.onboarding.invoke.search.everywhere.1",
                                       strong(toggleCase), code("AVERAGE")))
      text(PythonLessonsBundle.message("python.onboarding.invoke.search.everywhere.2",
                                       LessonUtil.rawKeyStroke(KeyEvent.VK_SHIFT), LessonUtil.actionName(it)))
      trigger(it)
      restoreIfModifiedOrMoved()
    }

    task {
      text(PythonLessonsBundle.message("python.onboarding.search.everywhere.description", strong("AVERAGE")))
      triggerByListItemAndHighlight { item ->
        (item as? GotoActionModel.MatchedValue)?.value?.let { GotoActionItemProvider.getActionText(it) } == toggleCase
      }
      restoreIfModifiedOrMoved()
      restoreState(delayMillis = defaultRestoreDelay) {
        UIUtil.getParentOfType(SearchEverywhereUI::class.java, focusOwner) == null
      }
    }

    actionTask("EditorToggleCase") {
      restoreByUi(delayMillis = defaultRestoreDelay)
      PythonLessonsBundle.message("python.onboarding.apply.action", strong(toggleCase), LessonUtil.rawEnter())
    }

    text(PythonLessonsBundle.message("python.onboarding.case.changed"))
  }

  private fun TaskRuntimeContext.runManager() = RunManager.getInstance(project)
  private fun TaskRuntimeContext.configurations() =
    runManager().allSettings.filter { it.name.contains(demoConfigurationName) }
}
