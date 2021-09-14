// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.essensial

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.RunManager
import com.intellij.execution.ui.layout.impl.JBRunnerTabs
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.actions.ToggleCaseAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.WindowStateService
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.FocusManagerImpl
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.StripeButton
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.ui.ScreenUtil
import com.intellij.ui.UIBundle
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.Alarm
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.xdebugger.XDebuggerBundle
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.ift.PythonLessonsBundle
import com.jetbrains.python.ift.PythonLessonsUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nls
import training.FeaturesTrainerIcons
import training.dsl.*
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
import training.util.invokeActionForFocusContext
import training.util.learningToolWindow
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.JWindow
import javax.swing.tree.TreePath

class PythonOnboardingTour :
  KLesson("python.onboarding", PythonLessonsBundle.message("python.onboarding.lesson.name")) {

  private lateinit var openLearnTaskId: TaskContext.TaskId
  private var useDelay: Boolean = false

  private val demoConfigurationName: String = "welcome"
  private val demoFileName: String = "$demoConfigurationName.py"

  private val uiSettings get() = UISettings.instance

  override val properties = LessonProperties(
    canStartInDumbMode = true,
    openFileAtStart = false
  )

  override val testScriptProperties = TaskTestContext.TestScriptProperties(skipTesting = true)

  private var backupPopupLocation: Point? = null
  private var hideToolStripesPreference = false
  private var showNavigationBarPreference = true

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
      useDelay = true
      configurations().forEach { runManager().removeConfiguration(it) }

      val root = ProjectUtils.getProjectRoot(project)
      if (root.findChild(demoFileName) == null) invokeLater {
        runWriteAction {
          root.createChildData(this, demoFileName)
        }
      }
    }
    clearBreakpoints()

    checkUiSettings()

    projectTasks()

    prepareSample(sample)

    openLearnToolwindow()

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

  override fun onLessonEnd(project: Project, lessonPassed: Boolean) {
    restorePopupPosition(project, SearchEverywhereManagerImpl.LOCATION_SETTINGS_KEY, backupPopupLocation)
    backupPopupLocation = null

    uiSettings.hideToolStripes = hideToolStripesPreference
    uiSettings.showNavigationBar = showNavigationBarPreference
    uiSettings.fireUISettingsChanged()

    if (!lessonPassed) return
    val dataContextPromise = DataManager.getInstance().dataContextFromFocusAsync
    invokeLater {
      val result = MessageDialogBuilder.yesNoCancel(PythonLessonsBundle.message("python.onboarding.finish.title"),
                                                    PythonLessonsBundle.message("python.onboarding.finish.text",
                                                                                LessonUtil.returnToWelcomeScreenRemark()))
        .yesText(PythonLessonsBundle.message("python.onboarding.finish.exit"))
        .noText(PythonLessonsBundle.message("python.onboarding.finish.modules"))
        .icon(FeaturesTrainerIcons.Img.PluginIcon)
        .show(project)

      when (result) {
        Messages.YES -> invokeLater {
          LessonManager.instance.stopLesson()
          val closeAction = ActionManager.getInstance().getAction("CloseProject") ?: error("No close project action found")
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
    }
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
      text(PythonLessonsBundle.message("python.onboarding.toggle.breakpoint.1",
                                       code("6.5"), code("find_average"), code("26")))
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

  private fun LessonContext.waitIndexingTasks() {
    task {
      triggerByUiComponentAndHighlight(highlightInside = false) { progress: NonOpaquePanel ->
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
      rehighlightPreviousUi = true
    }

    task {
      triggerByPartOfComponent(highlightInside = true, usePulsation = true) { ui: ActionToolbarImpl ->
        ui.takeIf { (ui.place == ActionPlaces.NAVIGATION_BAR_TOOLBAR || ui.place == ActionPlaces.MAIN_TOOLBAR) }?.let {
          val configurations = ui.components.find { it is JPanel && it.components.any { b -> b is ComboBoxAction.ComboBoxButton } }
          val stop = ui.components.find { it is ActionButton && it.action == ActionManager.getInstance().getAction("Stop") }
          if (configurations != null && stop != null) {
            val x = configurations.x
            val y = configurations.y
            val width = stop.x + stop.width - x
            val height = stop.y + stop.height - y
            Rectangle(x, y, width, height)
          }
          else null
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

  private fun LessonContext.openLearnToolwindow() {
    task {
      triggerByUiComponentAndHighlight(usePulsation = true) { stripe: StripeButton ->
        stripe.windowInfo.id == "Learn"
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
           LearningBalloonConfig(Balloon.Position.atRight, duplicateMessage = true, width = 0))
      triggerByFoundPathAndHighlight { _: JTree, path: TreePath ->
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
    val completionPosition = sample.getPosition(2)
    caret(completionPosition)
    prepareRuntimeTask {
      FocusManagerImpl.getInstance(project).requestFocusInProject(editor.contentComponent, project)
    }
    task {
      text(PythonLessonsBundle.message("python.onboarding.type.division", code(" / l")))
      proposeRestoreForInvalidText("/len")
      triggerByListItemAndHighlight(highlightBorder = true, highlightInside = false) { // no highlighting
        it.toString().contains("string=len;")
      }
    }

    task {
      text(PythonLessonsBundle.message("python.onboarding.choose.len.item",
                                       code("len(__obj)"), action("EditorChooseLookupItem")))
      stateCheck {
        checkEditorModification(completionPosition, "/len()")
      }
      restoreByUi()
    }

    task {
      text(PythonLessonsBundle.message("python.onboarding.invoke.completion",
                                       code("values"),
                                       code("()")))
      triggerByListItemAndHighlight(highlightBorder = true, highlightInside = false) { // no highlighting
        it.toString().contains("values")
      }
      proposeRestoreForInvalidText("values")
    }

    task {
      text(PythonLessonsBundle.message("python.onboarding.choose.values.item",
                                       code("values"), action("EditorChooseLookupItem")))
      stateCheck {
        checkEditorModification(completionPosition, "/len(values)")
      }
      restoreByUi()
    }
  }

  private fun TaskContext.proposeRestoreForInvalidText(needToType: String) {
    proposeRestore {
      checkExpectedStateOfEditor(previous.sample) {
        needToType.contains(it.replace(" ", ""))
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

    fun returnTypeMessage(project: Project) =
      if (PythonLessonsUtil.isPython3Installed(project)) PyPsiBundle.message("INTN.specify.return.type.in.annotation")
      else PyPsiBundle.message("INTN.specify.return.type.in.docstring")

    caret("find_average")
    task("ShowIntentionActions") {
      text(PythonLessonsBundle.message("python.onboarding.invoke.intention.for.code",
                                       code("find_average"), action(it)))
      triggerByListItemAndHighlight(highlightBorder = true, highlightInside = false) { item ->
        item.toString().contains(returnTypeMessage(project))
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
      triggerByUiComponentAndHighlight(highlightInside = false) { ui: ExtendableTextField ->
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
        if (adjustSearchEverywherePosition(popupWindow) || LessonUtil.adjustPopupPosition(project, popupWindow)) {
          backupPopupLocation = oldPopupLocation
        }
      }
      text(PythonLessonsBundle.message("python.onboarding.search.everywhere.description",
                                       strong("AVERAGE"), strong(PythonLessonsBundle.message("toggle.case.part"))))
      triggerByListItemAndHighlight { item ->
        val value = (item as? GotoActionModel.MatchedValue)?.value
        (value as? GotoActionModel.ActionWrapper)?.action is ToggleCaseAction
      }
      restoreByUi()
      restoreIfModifiedOrMoved()
    }

    actionTask("EditorToggleCase") {
      restoreByUi(delayMillis = defaultRestoreDelay)
      PythonLessonsBundle.message("python.onboarding.apply.action", strong(toggleCase), LessonUtil.rawEnter())
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
      triggerByUiComponentAndHighlight(usePulsation = true) { info: TextPanel.WithIconAndArrows ->
        info.toolTipText.contains(PyBundle.message("current.interpreter", ""))
      }
    }
    task {
      before {
        useDelay = false
      }
      text(PythonLessonsBundle.message("python.onboarding.interpreter.description"))

      restoreState(restoreId = openLearnTaskId) {
        learningToolWindow(project)?.isVisible?.not() ?: true
      }
      restoreIfModified(sample)
      proceedLink()
    }
    prepareRuntimeTask {
      LearningUiHighlightingManager.clearHighlights()
    }
  }

  private fun TaskRuntimeContext.runManager() = RunManager.getInstance(project)
  private fun TaskRuntimeContext.configurations() =
    runManager().allSettings.filter { it.name.contains(demoConfigurationName) }


  private fun TaskRuntimeContext.adjustSearchEverywherePosition(popupWindow: JWindow): Boolean {
    val indexOf = 4 + (editor.document.charsSequence.indexOf("8]))").takeIf { it > 0 } ?: return false)
    val endOfEditorText = editor.offsetToXY(indexOf)

    val locationOnScreen = editor.contentComponent.locationOnScreen

    val leftBorder = Point(locationOnScreen.x + endOfEditorText.x, locationOnScreen.y + endOfEditorText.y)
    val screenRectangle = ScreenUtil.getScreenRectangle(leftBorder)


    val learningToolWindow = learningToolWindow(project) ?: return false
    if (learningToolWindow.anchor != ToolWindowAnchor.LEFT) return false

    val popupBounds = popupWindow.bounds

    if (popupBounds.x > leftBorder.x) return false // ok, no intersection

    val rightScreenBorder = screenRectangle.x + screenRectangle.width
    if (leftBorder.x + popupBounds.width > rightScreenBorder) {
      val mainWindow = UIUtil.getParentOfType(IdeFrameImpl::class.java, editor.contentComponent) ?: return false
      val offsetFromBorder = leftBorder.x - mainWindow.x
      val needToShiftWindowX = rightScreenBorder - offsetFromBorder - popupBounds.width
      if (needToShiftWindowX < screenRectangle.x) return false // cannot shift the window back
      mainWindow.location = Point(needToShiftWindowX, mainWindow.location.y)
      popupWindow.location = Point(needToShiftWindowX + offsetFromBorder, popupBounds.y)
    }
    else {
      popupWindow.location = Point(leftBorder.x, popupBounds.y)
    }
    return true
  }
}
