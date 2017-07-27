package training.editor.eduUI

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.ui.GotItMessage
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Alarm
import com.intellij.util.Consumer
import training.learn.LearnBundle
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseEvent

/**
 * Created by karashevich on 26/10/15.
 */
internal class DemoModeWidget(project: Project) : EditorBasedWidget(
  project), StatusBarWidget.Multiframe, StatusBarWidget.TextPresentation, CaretListener, SelectionListener {

  private var currentEditor: FileEditor

  init {
    val selectedEditors = FileEditorManager.getInstance(project).selectedEditors
    currentEditor = selectedEditors[0]
  }


  override fun install(statusBar: StatusBar) {
    super.install(statusBar)
    val multicaster = EditorFactory.getInstance().eventMulticaster
    multicaster.addCaretListener(this, this)
    multicaster.addSelectionListener(this, this)
    myStatusBar.updateWidget(ID())

    val key = "demoWidget.info.shown"
    if (!PropertiesComponent.getInstance().isTrueValue(key)) {
      PropertiesComponent.getInstance().setValue(key, true.toString())
      val alarm = Alarm()
      alarm.addRequest({
                         GotItMessage.createMessage(LearnBundle.message("demoWidget.info.title",ApplicationNamesInfo.getInstance().getFullProductName()), LearnBundle.message(
                           "demoWidget.info.message",ApplicationNamesInfo.getInstance().getFullProductName()))
                           .setDisposable(this@DemoModeWidget)
                           .show(RelativePoint(myStatusBar.component, Point(10, 0)), Balloon.Position.above)
                         Disposer.dispose(alarm)
                       }, 20000)
    }

  }

  override fun selectionChanged(e: SelectionEvent) {
    if (e.editor is FileEditor)
      currentEditor = e.editor as FileEditor
    myStatusBar.updateWidget(ID())

  }

  override fun selectionChanged(event: FileEditorManagerEvent) {
    currentEditor = event.newEditor!!
    myStatusBar.updateWidget(ID())
  }

  override fun ID(): String = DEMO_MODE_WIDGET_ID

  override fun getPresentation(type: StatusBarWidget.PlatformType): StatusBarWidget.WidgetPresentation? = this

  override fun copy(): StatusBarWidget? = null

  override fun getText(): String = "DEMO MODE ON"

  override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

  override fun getTooltipText(): String? = LearnBundle.message("status.demoMode.tooltipText", ApplicationNamesInfo.getInstance().getFullProductName())

  override fun getClickConsumer(): Consumer<MouseEvent>? = null

  override fun caretPositionChanged(e: CaretEvent) { }

  override fun caretAdded(e: CaretEvent) { }

  override fun caretRemoved(e: CaretEvent) { }

  override fun getMaxPossibleText(): String = "DEMO MODE OFF"

  companion object {
    val DEMO_MODE_WIDGET_ID = "DemoMode"
  }
}