package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.left

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.AutoScrollToSourceHandler
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.api.query.language.PackageSearchQueryCompletionProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.ComponentActionWrapper
import com.jetbrains.packagesearch.intellij.plugin.ui.RiderUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchToolWindowModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.updateAndRepaint
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ItemEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.event.DocumentEvent
import net.miginfocom.swing.MigLayout

class PackagesSmartPanel(
    val viewModel: PackageSearchToolWindowModel,
    autoScrollToSourceHandler: AutoScrollToSourceHandler
) : PackageSearchPanelBase(PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.title")) {

    private val smartList = PackagesSmartList(viewModel)

    val searchTextField = PackagesSmartSearchField(viewModel, PackageSearchQueryCompletionProvider())
        .apply {
            goToList = {
                if (smartList.hasPackageItems) {
                    smartList.selectedIndex = smartList.firstPackageIndex
                    IdeFocusManager.getInstance(viewModel.project).requestFocus(smartList, false)
                    true
                } else {
                    false
                }
            }
        }

    private val packagesPanel = RiderUI.borderPanel {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }

    private val moduleContextComboBox = ModuleContextComboBox(viewModel)
    private val repositoryContextComboBox = RepositoryContextComboBox(viewModel)
    private val onlyStableCheckBox = RiderUI.checkBox(PackageSearchBundle.message("packagesearch.ui.toolwindow.onlystable"))
        .apply {
            isOpaque = false
            border = JBUI.Borders.empty(0, 6, 0, 0)
        }
    private val onlyMppCheckBox = RiderUI.checkBox(PackageSearchBundle.message("packagesearch.ui.toolwindow.onlympp"))
        .apply {
            isOpaque = false
            border = JBUI.Borders.empty(0, 6, 0, 0)
        }

    private fun updateLaf() {
        @Suppress("MagicNumber") // Gotta love Swing APIs
        with(searchTextField) {
            textEditor.putClientProperty("JTextField.Search.Gap", JBUI.scale(6))
            textEditor.putClientProperty("JTextField.Search.GapEmptyText", JBUI.scale(-1))
            textEditor.border = JBUI.Borders.empty(0, 6, 0, 0)
            textEditor.isOpaque = true
            textEditor.background = RiderUI.HeaderBackgroundColor
        }
    }

    private fun createActionGroup() = DefaultActionGroup().apply {
        add(ComponentActionWrapper { moduleContextComboBox })
        add(ComponentActionWrapper { repositoryContextComboBox })
        add(ComponentActionWrapper { onlyStableCheckBox })
        add(ComponentActionWrapper { onlyMppCheckBox })
    }

    private val mainToolbar = ActionManager.getInstance().createActionToolbar("", createActionGroup(), true).apply {
        component.background = RiderUI.HeaderBackgroundColor
        component.border = BorderFactory.createMatteBorder(0, JBUI.scale(1), 0, 0, JBUI.CurrentTheme.CustomFrameDecorations.paneBackground())
    }

    private val headerPanel = RiderUI.headerPanel {
        RiderUI.setHeight(this, RiderUI.MediumHeaderHeight)

        border = BorderFactory.createEmptyBorder()

        addToCenter(object : JPanel() {
            init {
                layout = MigLayout("ins 0, fill", "[left, fill, grow][right]", "center")
                add(searchTextField)
                add(mainToolbar.component)
            }

            override fun getBackground() = RiderUI.UsualBackgroundColor
        })
    }

    private val scrollPane = JBScrollPane(packagesPanel.apply {
        add(createListPanel(smartList))
        add(Box.createVerticalGlue())
    }, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER).apply {
        this.border = BorderFactory.createMatteBorder(JBUI.scale(1), 0, 0, 0, JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
        this.verticalScrollBar.unitIncrement = 16

        UIUtil.putClientProperty(verticalScrollBar, JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, true)
    }

    private fun createListPanel(list: PackagesSmartList) = RiderUI.borderPanel {
        minimumSize = Dimension(1, 1)
        maximumSize = Dimension(Int.MAX_VALUE, maximumSize.height)
        add(list, BorderLayout.NORTH)
        RiderUI.updateParentHeight(list)
    }

    init {
        viewModel.searchTerm.set("")

        viewModel.isBusy.advise(viewModel.lifetime) {
            searchTextField.isEnabled = !it
        }

        smartList.transferFocusUp = {
            IdeFocusManager.getInstance(viewModel.project).requestFocus(searchTextField, false)
        }

        searchTextField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                ApplicationManager.getApplication().invokeLater {
                    viewModel.searchTerm.set(searchTextField.text)
                }
            }
        })
        viewModel.searchTerm.advise(viewModel.lifetime) { searchTerm ->
            if (searchTextField.text != searchTerm) {
                searchTextField.text = searchTerm
            }
        }

        onlyStableCheckBox.addItemListener { e ->
            viewModel.selectedOnlyStable.set(e.stateChange == ItemEvent.SELECTED)
        }
        viewModel.selectedOnlyStable.advise(viewModel.lifetime) { selected ->
            onlyStableCheckBox.isSelected = selected
        }

        onlyMppCheckBox.addItemListener { e ->
            viewModel.selectedOnlyMpp.set(e.stateChange == ItemEvent.SELECTED)
        }
        viewModel.selectedOnlyMpp.advise(viewModel.lifetime) { selected ->
            onlyMppCheckBox.isSelected = selected
        }

        viewModel.searchResultsUpdated.advise(viewModel.lifetime) {
            smartList.updateAllPackages(it.values.toList())
            packagesPanel.updateAndRepaint()
        }

        viewModel.isSearching.advise(viewModel.lifetime) {
            smartList.availableHeader.setProgressVisibility(it)
            moduleContextComboBox.updateAndRepaint()
            repositoryContextComboBox.updateAndRepaint()
            onlyStableCheckBox.updateAndRepaint()
            onlyMppCheckBox.updateAndRepaint()
            headerPanel.updateAndRepaint()
            smartList.updateAndRepaint()
            packagesPanel.updateAndRepaint()
        }

        viewModel.isFetchingSuggestions.advise(viewModel.lifetime) {
            smartList.installedHeader.setProgressVisibility(it)
            smartList.updateAndRepaint()
            packagesPanel.updateAndRepaint()
        }

        autoScrollToSourceHandler.install(smartList)
        smartList.addPackageSelectionListener {
            viewModel.selectedPackage.set(it.identifier)
            autoScrollToSourceHandler.onMouseClicked(smartList)
        }

        // LaF
        val lafListener = LafManagerListener { updateLaf() }
        updateLaf()
        LafManager.getInstance().addLafManagerListener(lafListener)
        Disposer.register(viewModel.project, Disposable {
            LafManager.getInstance().removeLafManagerListener(lafListener)
        })
    }

    override fun build() = RiderUI.boxPanel {
        add(headerPanel)
        add(scrollPane)

        @Suppress("MagicNumber") // Swing APIs are <3
        minimumSize = Dimension(JBUI.scale(200), minimumSize.height)
    }
}
