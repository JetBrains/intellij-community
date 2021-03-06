package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.right

import com.intellij.openapi.util.NlsContexts.ListItem
import com.intellij.util.ui.JBUI
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.RiderUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchToolWindowModel
import net.miginfocom.swing.MigLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JLabel
import javax.swing.JPanel

class PackagesChosenVersionView(val viewModel: PackageSearchToolWindowModel) {
    private val panelHeight = 48

    val versionsComboBox = RiderUI.comboBox(arrayOf(""))
    private val versionsModel = versionsComboBox.model as DefaultComboBoxModel<String>

    // panel for apply/remove buttons
    val buttonPanel = object : JPanel(MigLayout("ins 0, fill, gap 0")) {
        init {
            border = JBUI.Borders.empty(0, 0, 0, 0)
        }

        override fun getBackground() = RiderUI.UsualBackgroundColor
    }

    val panel = RiderUI.borderPanel {
        RiderUI.setHeight(this, panelHeight, true)
        border = JBUI.Borders.empty(-2, 0, 4, 0)

        addToLeft(RiderUI.flowPanel {
            add(JLabel(PackageSearchBundle.message("packagesearch.ui.toolwindow.label.version")))
            add(versionsComboBox)
            add(buttonPanel)
        })
    }

    init {
        viewModel.isBusy.advise(viewModel.lifetime) { isBusy ->
            versionsComboBox.isEnabled = !isBusy
            buttonPanel.isEnabled = !isBusy
            buttonPanel.components.forEach { it.isEnabled = !isBusy }
        }

        RiderUI.overrideKeyStroke(versionsComboBox, "shift ENTER") { versionsComboBox.transferFocusBackward() }
        RiderUI.overrideKeyStroke(versionsComboBox, "LEFT") { versionsComboBox.transferFocusBackward() }
        RiderUI.overrideKeyStroke(versionsComboBox, "ENTER") { versionsComboBox.transferFocus() }
    }

    fun getSelectedVersion() = if (versionsComboBox.selectedIndex == -1) "" else ((versionsModel.selectedItem ?: "") as String)

    fun refreshUI(versions: List<String>?, @ListItem defaultVersion: String?) {
        if (versions == null || versions.isEmpty()) {
            versionsComboBox.isEnabled = false
            versionsModel.removeAllElements()
            versionsModel.addElement(defaultVersion ?: "")
            return
        }

        versionsComboBox.isEnabled = false
        versionsModel.removeAllElements()
        for (version in versions) {
            versionsModel.addElement(version)
        }
        versionsComboBox.isEnabled = true
    }

    fun show() {
        panel.isVisible = true
    }

    fun hide() {
        panel.isVisible = false
    }
}
