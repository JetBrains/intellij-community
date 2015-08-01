package org.jetbrains.settingsRepository

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBTabsPaneImpl
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.ArrayUtil
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Insets
import javax.swing.*
import javax.swing.border.Border
import javax.swing.border.EmptyBorder

class IcsSettingsEditor(private val project: Project?) : DialogWrapper(project, true) {
  val upstreamEditor = IcsSettingsPanel(project, getContentPane()!!, { doOKAction() })

  init {
    setTitle(IcsBundle.message("settings.panel.title"))
    init()
  }

  private var currentConfigurable: Configurable? = null

  override fun createCenterPanel(): JComponent? {
    upstreamEditor.panel.setBorder(DialogWrapper.ourDefaultBorder)

    val tabbedPane = JBTabsPaneImpl(null, SwingConstants.TOP, myDisposable)
    val tabs = tabbedPane.getTabs() as JBTabsImpl
    tabs.setSizeBySelected(true)

    var actions = arrayOf(getOKAction(), getCancelAction())
    if (SystemInfo.isMac) {
      actions = ArrayUtil.reverseArray(actions)
    }

    val readOnlySourcesEditor = createReadOnlySourcesEditor(getContentPane(), project)

    tabs.addTab(TabInfo(wrap(upstreamEditor.panel, upstreamEditor.createActions())).setText("Upstream"))
    tabs.addTab(TabInfo(wrap(readOnlySourcesEditor.getComponent(), actions)).setText("Read-only Sources").setObject(readOnlySourcesEditor))

    tabs.addListener(object : TabsListener.Adapter() {
      override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
        pack()
        currentConfigurable = newSelection?.getObject() as Configurable?
      }
    })
    return tabbedPane.getComponent()
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return upstreamEditor.urlTextField
  }

  override fun doOKAction() {
    currentConfigurable?.apply()
    saveSettings(icsManager.settings)

    super.doOKAction()
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

interface Configurable {
  fun isModified(): Boolean

  fun apply()

  fun reset()

  fun getComponent(): JComponent
}