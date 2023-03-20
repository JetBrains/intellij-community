// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.essensial

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.execution.RunManager
import com.intellij.execution.console.DuplexConsoleListener
import com.intellij.execution.console.DuplexConsoleView
import com.intellij.execution.ui.UIExperiment
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.actions.ToggleCaseAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.WindowStateService
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.ui.IdeUICustomization
import com.intellij.ui.UIBundle
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.Alarm
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.ift.PythonLessonsBundle
import com.jetbrains.python.ift.PythonLessonsUtil
import com.jetbrains.python.sdk.pythonSdk
import training.FeaturesTrainerIcons
import training.dsl.*
import training.dsl.LessonUtil.adjustSearchEverywherePosition
import training.dsl.LessonUtil.checkEditorModification
import training.dsl.LessonUtil.checkExpectedStateOfEditor
import training.dsl.LessonUtil.restoreIfModified
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.dsl.LessonUtil.restorePopupPosition
import training.learn.LearnBundle
import training.learn.LessonsBundle
import training.learn.course.KLesson
import training.learn.course.LessonProperties
import training.learn.lesson.LessonManager
import training.learn.lesson.general.run.clearBreakpoints
import training.learn.lesson.general.run.toggleBreakpointTask
import training.project.ProjectUtils
import training.ui.LearningUiHighlightingManager
import training.ui.LearningUiManager
import training.ui.getFeedbackProposedPropertyName
import training.util.*
import java.awt.Point
import java.awt.event.KeyEvent
import javax.swing.JTree
import javax.swing.JWindow
import javax.swing.tree.TreePath

class PythonOnboardingTourLesson :
  KLesson("python.onboarding", PythonLessonsBundle.message("python.onboarding.lesson.name")) {

  private lateinit var openLearnTaskId: TaskContext.TaskId
  private var useDelay: Boolean = false

  private val demoFileName: String = "welcome.py"

  private val uiSettings get() = UISettings.getInstance()

  override val properties = LessonProperties(
    canStartInDumbMode = true,
    openFileAtStart = false
  )

  override val testScriptProperties = TaskTestContext.TestScriptProperties(skipTesting = true)

  private var backupPopupLocation: Point? = null
  private var hideToolStripesPreference = false
  private var showNavigationBarPreference = true

  private var usedInterpreterAtStart: String = "undefined"

  val sample: LessonSample = parseLessonSample("""
    def find_average(values)<caret id=3/>:
        result = 0
        for v in values:
            result += v
        <caret>return result<caret id=2/>


    print("AVERAGE", find_average([5,6, 7, 8]))
  """.trimIndent())

  override val lessonContent: LessonContext.() -> Unit = {
    prepareRuntimeTask {
      usedInterpreterAtStart = project.pythonSdk?.versionString ?: "none"
      useDelay = true
      invokeActionForFocusContext(getActionById("Stop"))
      val runManager = RunManager.getInstance(project)
      runManager.allSettings.forEach(runManager::removeConfiguration)

      val root = ProjectUtils.getCurrentLearningProjectRoot()
      if (root.findChild(demoFileName) == null) invokeLater {
        runWriteAction {
          root.createChildData(this, demoFileName)
        }
      }
    }
    clearBreakpoints()

    checkUiSettings()

    projectTasks()

    prepareSample(sample, checkSdkConfiguration = false)

    openLearnToolwindow()

    sdkConfigurationTasks()

    showInterpreterConfiguration()

    waitIndexingTasks()

    runTasks()

    debugTasks()

    completionSteps()

    waitBeforeContinue(500)

    contextActions()

    waitBeforeContinue(500)

    searchEverywhereTasks()

    task {
      text(PythonLessonsBundle.message("python.onboarding.epilog",
                                       getCallBackActionId("CloseProject"),
                                       LessonUtil.returnToWelcomeScreenRemark(),
                                       LearningUiManager.addCallback { LearningUiManager.resetModulesView() }))
    }
  }

  override fun onLessonEnd(project: Project, lessonEndInfo: LessonEndInfo) {
    val primaryLanguage = module.primaryLanguage
    if (primaryLanguage == null) {
      thisLogger().error("Onboarding lesson has no language support for some magical reason")
      return
    }
    PythonLessonsUtil.prepareFeedbackDataForOnboardingLesson(
      project,
      getFeedbackProposedPropertyName(primaryLanguage),
      "PyCharm Onboarding Tour Feedback",
      "pycharm_onboarding_tour",
      primaryLanguage,
      lessonEndInfo,
      usedInterpreterAtStart,
    )
    restorePopupPosition(project, SearchEverywhereManagerImpl.LOCATION_SETTINGS_KEY, backupPopupLocation)
    backupPopupLocation = null

    uiSettings.hideToolStripes = hideToolStripesPreference
    uiSettings.showNavigationBar = showNavigationBarPreference
    uiSettings.fireUISettingsChanged()

    if (!lessonEndInfo.lessonPassed) {
      LessonUtil.showFeedbackNotification(this, project)
      return
    }
    val dataContextPromise = DataManager.getInstance().dataContextFromFocusAsync
    invokeLater {
      val result = MessageDialogBuilder.yesNoCancel(PythonLessonsBundle.message("python.onboarding.finish.title"),
                                                    PythonLessonsBundle.message("python.onboarding.finish.text",
                                                                                LessonUtil.returnToWelcomeScreenRemark()))
        .yesText(PythonLessonsBundle.message("python.onboarding.finish.exit"))
        .noText(PythonLessonsBundle.message("python.onboarding.finish.modules"))
        .icon(FeaturesTrainerIcons.PluginIcon)
        .show(project)

      when (result) {
        Messages.YES -> invokeLater {
          LessonManager.instance.stopLesson()
          val closeAction = getActionById("CloseProject")
          dataContextPromise.onSuccess { context ->
            invokeLater {
              val event = AnActionEvent.createFromAnAction(closeAction, null, ActionPlaces.LEARN_TOOLWINDOW, context)
              ActionUtil.performActionDumbAwareWithCallbacks(closeAction, event)
            }
          }
        }
        Messages.NO -> invokeLater {
          LearningUiManager.resetModulesView()
        }
      }
      if (result != Messages.YES) {
        LessonUtil.showFeedbackNotification(this, project)
      }
    }
  }

  private fun getCallBackActionId(@Suppress("SameParameterValue") actionId: String): Int {
    val action = getActionById(actionId)
    return LearningUiManager.addCallback { invokeActionForFocusContext(action) }
  }

  private fun LessonContext.debugTasks() {
    clearBreakpoints()

    var logicalPosition = LogicalPosition(0, 0)
    prepareRuntimeTask {
      logicalPosition = editor.offsetToLogicalPosition(sample.startOffset)
    }
    caret(sample.startOffset)

    toggleBreakpointTask(sample, { logicalPosition }, breakpointXRange = { IntRange(13, it - 17) }, checkLine = false) {
      text(PythonLessonsBundle.message("python.onboarding.balloon.click.here"),
           LearningBalloonConfig(Balloon.Position.below, width = 0, cornerToPointerDistance = 20))
      text(PythonLessonsBundle.message("python.onboarding.toggle.breakpoint.1",
                                       code("6.5"), code("find_average"), code("26")))
      text(PythonLessonsBundle.message("python.onboarding.toggle.breakpoint.2"))
    }

    highlightButtonById("Debug", highlightInside = false, usePulsation = false)

    actionTask("Debug") {
      showBalloonOnHighlightingComponent(PythonLessonsBundle.message("python.onboarding.balloon.start.debugging"))
      restoreState {
        lineWithBreakpoints() != setOf(logicalPosition.line)
      }
      restoreIfModified(sample)
      PythonLessonsBundle.message("python.onboarding.start.debugging", icon(AllIcons.Actions.StartDebugger))
    }

    highlightDebugActionsToolbar()

    task {
      rehighlightPreviousUi = true
      gotItStep(Balloon.Position.above, width = 0,
                PythonLessonsBundle.message("python.onboarding.balloon.about.debug.panel",
                                            strong(UIBundle.message("tool.window.name.debug")),
                                            strong(LessonsBundle.message("debug.workflow.lesson.name"))))
      restoreIfModified(sample)
    }

    highlightButtonById("Stop", highlightInside = false, usePulsation = false)
    task {
      val position = if (UIExperiment.isNewDebuggerUIEnabled()) Balloon.Position.above else Balloon.Position.atRight
      showBalloonOnHighlightingComponent(PythonLessonsBundle.message("python.onboarding.balloon.stop.debugging"),
                                         position) { list -> list.maxByOrNull { it.locationOnScreen.y } }
      text(PythonLessonsBundle.message("python.onboarding.stop.debugging",
                                       icon(AllIcons.Actions.Suspend)))
      restoreIfModified(sample)
      addFutureStep {
        val process = XDebuggerManager.getInstance(project).currentSession?.debugProcess
        val console = process?.createConsole() as? DuplexConsoleView<*, *>
        if (console != null) {
          // When debug process terminates the console tab become activated and brings focus.
          // So, we need to finish this task only after tab is activated.
          // And then focus will be returned to editor in the start of completion tasks.
          console.addSwitchListener(DuplexConsoleListener { completeStep() }, taskDisposable)
        }
        else completeStep()
      }
      stateCheck {
        XDebuggerManager.getInstance(project).currentSession == null
      }
    }

    prepareRuntimeTask {
      LearningUiHighlightingManager.clearHighlights()
    }
  }

  private fun LessonContext.waitIndexingTasks() {
    task {
      triggerAndBorderHighlight().component { progress: NonOpaquePanel ->
        progress.javaClass.name.contains("InlineProgressPanel")
      }
    }

    task {
      text(PythonLessonsBundle.message("python.onboarding.indexing.description"))
      waitSmartModeStep()
    }

    waitBeforeContinue(300)

    prepareRuntimeTask {
      LearningUiHighlightingManager.clearHighlights()
    }
  }

  private fun LessonContext.runTasks() {
    highlightRunToolbar(highlightInside = false, usePulsation = false)

    task {
      triggerUI {
        clearPreviousHighlights = false
      }.component { ui: ActionButton -> ActionManager.getInstance().getId(ui.action) == "Run" }
    }

    task {
      val introductionText = PythonLessonsBundle.message("python.onboarding.temporary.configuration.description",
                                                         strong(ActionsBundle.actionText("NewUiRunWidget")),
                                                         icon(AllIcons.Actions.Execute),
                                                         icon(AllIcons.Actions.StartDebugger))

      @Suppress("DEPRECATION")
      val runOptionsText = if (PlatformUtils.isPyCharmPro()) {
        " " + PythonLessonsBundle.message("python.onboarding.run.options.professional",
                                          icon(AllIcons.Actions.Profile),
                                          icon(AllIcons.General.RunWithCoverage),
                                          icon(AllIcons.Actions.More))
      }
      else ""
      text("$introductionText$runOptionsText")
      text(PythonLessonsBundle.message("python.onboarding.run.sample", icon(AllIcons.Actions.Execute), action("Run")))
      text(PythonLessonsBundle.message("python.onboarding.run.sample.balloon", icon(AllIcons.Actions.Execute), action("Run")),
           LearningBalloonConfig(Balloon.Position.below, 0))
      checkToolWindowState("Run", true)
      restoreIfModified(sample)
    }
  }

  private fun LessonContext.openLearnToolwindow() {
    task {
      triggerAndBorderHighlight().component { stripe: ActionButton ->
        stripe.action.templateText == LearnBundle.message("toolwindow.stripe.Learn")
      }
    }

    task {
      openLearnTaskId = taskId
      text(PythonLessonsBundle.message("python.onboarding.balloon.open.learn.toolbar", strong(LearnBundle.message("toolwindow.stripe.Learn"))),
           LearningBalloonConfig(Balloon.Position.atRight, width = 0, duplicateMessage = true))
      stateCheck {
        ToolWindowManager.getInstance(project).getToolWindow("Learn")?.isVisible == true
      }
      restoreIfModified(sample)
    }

    prepareRuntimeTask {
      LearningUiHighlightingManager.clearHighlights()
      requestEditorFocus()
    }
  }


  private fun LessonContext.checkUiSettings() {
    hideToolStripesPreference = uiSettings.hideToolStripes
    showNavigationBarPreference = uiSettings.showNavigationBar

    showInvalidDebugLayoutWarning()

    if (!hideToolStripesPreference && (showNavigationBarPreference || uiSettings.showMainToolbar)) {
      // a small hack to have same tasks count. It is needed to track statistics result.
      task { }
      task { }
      return
    }

    task {
      text(PythonLessonsBundle.message("python.onboarding.change.ui.settings"))
      proceedLink()
    }

    prepareRuntimeTask {
      uiSettings.hideToolStripes = false
      uiSettings.showNavigationBar = true
      uiSettings.fireUISettingsChanged()
    }
  }

  private fun LessonContext.projectTasks() {
    prepareRuntimeTask {
      LessonUtil.hideStandardToolwindows(project)
    }
    task {
      triggerAndBorderHighlight().component { stripe: ActionButton ->
        stripe.action.templateText == IdeUICustomization.getInstance().getProjectViewTitle(project)
      }
    }

    task {
      var collapsed = false

      text(PythonLessonsBundle.message("python.onboarding.project.view.description",
                                       action("ActivateProjectToolWindow")))
      text(PythonLessonsBundle.message("python.onboarding.balloon.project.view"),
           LearningBalloonConfig(Balloon.Position.atRight, width = 0, cornerToPointerDistance = 8))
      triggerAndBorderHighlight().treeItem { tree: JTree, path: TreePath ->
        val result = path.pathCount >= 1 && path.getPathComponent(0).isToStringContains("PyCharmLearningProject")
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
      path.pathCount >= 3 && path.getPathComponent(2).isToStringContains(demoFileName)

    task {
      text(PythonLessonsBundle.message("python.onboarding.balloon.project.directory"),
           LearningBalloonConfig(Balloon.Position.atRight, duplicateMessage = true, width = 0))
      triggerAndBorderHighlight().treeItem { _: JTree, path: TreePath ->
        isDemoFilePath(path)
      }
      restoreByUi()
    }

    task {
      text(PythonLessonsBundle.message("python.onboarding.balloon.open.file", strong(demoFileName)),
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
    prepareRuntimeTask {
      setSample(sample.insertAtPosition(2, " / len(<caret>)"))
    }

    task {
      text(PythonLessonsBundle.message("python.onboarding.type.division",
        code(" / len()")))
      text(PythonLessonsBundle.message("python.onboarding.invoke.completion",
        code("values"),
        code("()"),
        action("CodeCompletion")))
      triggerAndBorderHighlight().listItem { // no highlighting
        it.isToStringContains("values")
      }
      proposeRestoreForInvalidText("values")
    }

    task {
      text(PythonLessonsBundle.message("python.onboarding.choose.values.item",
                                       code("values"), action("EditorChooseLookupItem")))
      stateCheck {
        checkEditorModification(sample, modificationPositionId = 2, needChange = "/len(values)")
      }
      restoreByUi()
    }
  }

  private fun LessonContext.contextActions() {
    val reformatMessage = PyBundle.message("QFIX.reformat.file")
    caret(",6")
    task("ShowIntentionActions") {
      text(PythonLessonsBundle.message("python.onboarding.invoke.intention.for.warning.1"))
      text(PythonLessonsBundle.message("python.onboarding.invoke.intention.for.warning.2", action(it)))
      triggerAndBorderHighlight().listItem { item ->
        item.isToStringContains(reformatMessage)
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

    fun returnTypeMessage(project: Project) =
      if (PythonLessonsUtil.isPython3Installed(project)) PyPsiBundle.message("INTN.specify.return.type.in.annotation")
      else PyPsiBundle.message("INTN.specify.return.type.in.docstring")

    caret("find_average")
    task("ShowIntentionActions") {
      text(PythonLessonsBundle.message("python.onboarding.invoke.intention.for.code",
                                       code("find_average"), action(it)))
      triggerAndBorderHighlight().listItem { item ->
        item.isToStringContains(returnTypeMessage(project))
      }
      restoreIfModifiedOrMoved()
    }

    task {
      text(PythonLessonsBundle.message("python.onboarding.apply.intention", strong(returnTypeMessage(project)), LessonUtil.rawEnter()))
      stateCheck {
        val text = editor.document.text
        previous.sample.text != text && text.contains("object") && !text.contains("values: object")
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
      triggerAndBorderHighlight().component { ui: ExtendableTextField ->
        UIUtil.getParentOfType(SearchEverywhereUI::class.java, ui) != null
      }
      restoreIfModifiedOrMoved()
    }

    task {
      transparentRestore = true
      before {
        if (backupPopupLocation != null) return@before
        val ui = previous.ui ?: return@before
        val popupWindow = UIUtil.getParentOfType(JWindow::class.java, ui) ?: return@before
        val oldPopupLocation = WindowStateService.getInstance(project).getLocation(SearchEverywhereManagerImpl.LOCATION_SETTINGS_KEY)
        if (adjustSearchEverywherePosition(popupWindow, "8]))") || LessonUtil.adjustPopupPosition(project, popupWindow)) {
          backupPopupLocation = oldPopupLocation
        }
      }
      text(PythonLessonsBundle.message("python.onboarding.search.everywhere.description",
                                       code("AVERAGE"), code(PythonLessonsBundle.message("toggle.case.part"))))
      triggerAndBorderHighlight().listItem { item ->
        val value = (item as? GotoActionModel.MatchedValue)?.value
        (value as? GotoActionModel.ActionWrapper)?.action is ToggleCaseAction
      }
      restoreByUi()
      restoreIfModifiedOrMoved()
    }

    task {
      text(PythonLessonsBundle.message("python.onboarding.apply.action", strong(toggleCase), LessonUtil.rawEnter()))
      stateCheck {
        editor.document.text.contains("\"average")
      }
      restoreByUi(delayMillis = defaultRestoreDelay)
    }

    text(PythonLessonsBundle.message("python.onboarding.case.changed"))
  }

  private fun LessonContext.showInterpreterConfiguration() {
    task {
      addFutureStep {
        if (useDelay) {
          Alarm().addRequest({ completeStep() }, 500)
        }
        else {
          completeStep()
        }
      }
    }

    task {
      triggerAndBorderHighlight().component { info: TextPanel.WithIconAndArrows ->
        info.toolTipText.isToStringContains(PyBundle.message("current.interpreter", ""))
      }
    }
    task {
      before {
        useDelay = false
      }
      text(PythonLessonsBundle.message("python.onboarding.interpreter.description"))
      gotItStep(Balloon.Position.above, 0, PythonLessonsBundle.message("python.onboarding.interpreter.tip"), duplicateMessage = false)

      restoreState(restoreId = openLearnTaskId) {
        learningToolWindow(project)?.isVisible?.not() ?: true
      }
      restoreIfModified(sample)
    }
    prepareRuntimeTask {
      LearningUiHighlightingManager.clearHighlights()
    }
  }
}
