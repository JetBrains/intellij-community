package org.jetbrains.settingsRepository

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.project.Project
import javax.swing.JComponent
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.table.TableModelEditor
import com.intellij.util.Function
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import javax.swing.JTextField
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.ui.DocumentAdapter
import javax.swing.event.DocumentEvent
import com.intellij.openapi.util.text.StringUtil
import java.awt.Component
import javax.swing.border.Border
import javax.swing.border.EmptyBorder
import com.intellij.util.ui.UIUtil
import com.intellij.ui.JBTabsPaneImpl
import javax.swing.SwingConstants
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import java.awt.Container
import javax.swing.Action
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ArrayUtil
import javax.swing.AbstractAction
import java.awt.event.ActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import org.jetbrains.settingsRepository.actions.NOTIFICATION_GROUP
import com.intellij.notification.NotificationType
import java.awt.BorderLayout
import javax.swing.JPanel
import com.intellij.ui.tabs.impl.JBTabsImpl
import javax.swing.Box
import com.intellij.ui.IdeBorderFactory
import java.awt.Insets

class IcsSettingsEditor(project: Project?) : DialogWrapper(project, true) {
  {
    setTitle(IcsBundle.message("settings.panel.title"))
    init()
  }

  override fun createCenterPanel(): JComponent? {
    val upstreamEditor = IcsSettingsPanel(null, getContentPane()!!, { doOKAction() })
    upstreamEditor.panel.setBorder(DialogWrapper.ourDefaultBorder)

    val tabbedPane = JBTabsPaneImpl(null, SwingConstants.TOP, myDisposable)
    val tabs = tabbedPane.getTabs() as JBTabsImpl
    tabs.setSizeBySelected(true)

    var actions = array(getOKAction(), getCancelAction())
    if (SystemInfo.isMac) {
      actions = ArrayUtil.reverseArray(actions)
    }

    val upstreamTabInfo = TabInfo(wrap(upstreamEditor.panel, upstreamEditor.createActions())).setText("Upstream")
    val readOnlySourcesEditor = createReadOnlySourcesEditor(getContentPane())
    val sourcesTabInfo = TabInfo(wrap(readOnlySourcesEditor.getComponent(), actions)).setText("Read-only Sources")

    tabs.addTab(upstreamTabInfo)
    tabs.addTab(sourcesTabInfo)

    tabs.addListener(object : TabsListener.Adapter() {
      override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
        pack()
      }
    })
    return tabbedPane.getComponent()
  }

  private fun wrap(component: JComponent, actions: Array<Action>): JComponent {
    val panel = JPanel(BorderLayout())
    panel.add(component, BorderLayout.CENTER)

    val buttonsPanel = Box.createHorizontalBox()
    buttonsPanel.setBorder(IdeBorderFactory.createEmptyBorder(Insets(8, 0, 0, 0)))
    buttonsPanel.add(Box.createHorizontalGlue())
    for (action in actions) {
      buttonsPanel.add(createJButtonForAction(action))
    }
    panel.add(buttonsPanel, BorderLayout.SOUTH)
    return panel
  }

  override fun createSouthPanel() = null

  override protected fun createContentPaneBorder(): Border {
    val insets = UIUtil.PANEL_REGULAR_INSETS
    return EmptyBorder(insets.top, 0, insets.bottom, 0)
  }
}

trait Configurable {
  fun isModified(): Boolean

  fun apply()

  fun reset()

  fun getComponent(): JComponent
}