package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.fus.FUSGroupIds
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.ComponentActionWrapper
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.FilterOptions
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.KnownRepositories
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.OperationExecutor
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.SearchClient
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.SelectedPackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.TargetModules
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.operations.PackageSearchOperationFactory
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.PackageSearchPanelBase
import com.jetbrains.packagesearch.intellij.plugin.ui.updateAndRepaint
import com.jetbrains.packagesearch.intellij.plugin.ui.util.Displayable
import com.jetbrains.packagesearch.intellij.plugin.ui.util.emptyBorder
import com.jetbrains.packagesearch.intellij.plugin.ui.util.onOpacityChanged
import com.jetbrains.packagesearch.intellij.plugin.ui.util.onVisibilityChanged
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaledEmptyBorder
import com.jetbrains.packagesearch.intellij.plugin.util.AppUI
import com.jetbrains.packagesearch.intellij.plugin.util.TraceInfo
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import com.jetbrains.packagesearch.intellij.plugin.util.logDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ItemEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.event.DocumentEvent

internal class PackagesListPanel(
    private val project: Project,
    private val searchClient: SearchClient,
    operationFactory: PackageSearchOperationFactory,
    operationExecutor: OperationExecutor,
    onItemSelectionChanged: SelectedPackageModelListener
) : PackageSearchPanelBase(PackageSearchBundle.message("packagesearch.ui.toolwindow.tab.packages.title")), Displayable<PackagesListPanel.ViewModel> {

    val selectedPackage = MutableStateFlow<SelectedPackageModel<*>?>(null)

    private val searchFieldFocus = Channel<Unit>()

    private val packagesTable = PackagesTable(project, operationExecutor, operationFactory, onItemSelectionChanged)

    private val searchTextField = PackagesSmartSearchField(searchFieldFocus.consumeAsFlow(), project)
        .apply {
            goToTable = {
                if (packagesTable.hasInstalledItems) {
                    packagesTable.selectedIndex = packagesTable.firstPackageIndex
                    IdeFocusManager.getInstance(project).requestFocus(packagesTable, false)
                    true
                } else {
                    false
                }
            }
            fieldClearedListener = {
                PackageSearchEventsLogger.logSearchQueryClear()
            }
        }

    private val packagesPanel = PackageSearchUI.borderPanel {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }

    private val onlyStableCheckBox = PackageSearchUI.checkBox(PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.filter.onlyStable"))
        .apply {
            isOpaque = false
            border = scaledEmptyBorder(left = 6)
        }

    private val onlyKotlinMpCheckBox = PackageSearchUI.checkBox(PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.filter.onlyMpp"))
        .apply {
            isOpaque = false
            border = scaledEmptyBorder(left = 6)
        }

    private val mainToolbar = ActionManager.getInstance().createActionToolbar("Packages.Manage", createActionGroup(), true).apply {
        setTargetComponent(toolbar)
        component.background = PackageSearchUI.HeaderBackgroundColor
        component.border = BorderFactory.createMatteBorder(0, 1.scaled(), 0, 0, JBUI.CurrentTheme.CustomFrameDecorations.paneBackground())
    }

    private fun createActionGroup() = DefaultActionGroup().apply {
        add(ComponentActionWrapper { onlyStableCheckBox })
        add(ComponentActionWrapper { onlyKotlinMpCheckBox })
    }

    private val searchPanel = PackageSearchUI.headerPanel {
        PackageSearchUI.setHeight(this, PackageSearchUI.MediumHeaderHeight)

        border = BorderFactory.createEmptyBorder()

        addToCenter(object : JPanel() {
            init {
                layout = MigLayout("ins 0, fill", "[left, fill, grow][right]", "center")
                add(searchTextField)
                add(mainToolbar.component)
            }

            override fun getBackground() = PackageSearchUI.UsualBackgroundColor
        })
    }

    private val headerPanel = HeaderPanel {
        logDebug("PackagesListPanel.headerPanel#onUpdateAllLinkClicked()") {
            "The user has clicked the update all link. This will cause ${it.size} operation(s) to be executed."
        }
        operationExecutor.executeOperations(it)
    }

    private val tableScrollPane = JBScrollPane(
        packagesPanel.apply {
            add(packagesTable)
            add(Box.createVerticalGlue())
        },
        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    ).apply {
        border = emptyBorder()
        viewportBorder = emptyBorder()
        viewport.scrollMode = JViewport.SIMPLE_SCROLL_MODE
        verticalScrollBar.apply {
            headerPanel.adjustForScrollbar(isVisible, isOpaque)

            // Here we should make sure we set IGNORE_SCROLLBAR_IN_INSETS, but alas it doesn't work with JTables
            // as of IJ 2020.3 (see JBViewport#updateBorder()). If it did, we could just set:
            // UIUtil.putClientProperty(this, JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, false)
            // Instead, we have to work around the issue, inferring if the scrollbar is "floating" by looking at
            // its isOpaque property — since Swing maps the opacity of scrollbars to whether they're "floating"
            // (e.g., on macOS, System Preferences > General > Show scroll bars: "When scrolling")
            onOpacityChanged { newIsOpaque ->
                headerPanel.adjustForScrollbar(isVisible, newIsOpaque)
            }
            onVisibilityChanged { newIsVisible ->
                headerPanel.adjustForScrollbar(newIsVisible, isOpaque)
            }
        }
    }

    private val listPanel = JBPanelWithEmptyText().apply {
        emptyText.text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.empty.base")
        layout = BorderLayout()
        add(tableScrollPane, BorderLayout.CENTER)
        background = PackageSearchUI.UsualBackgroundColor
        border = BorderFactory.createMatteBorder(1.scaled(), 0, 0, 0, JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
    }

    init {
        searchClient.setSearchQuery("")

        registerForUiEvents()

        callbackFlow {
            val conn = project.messageBus.simpleConnect()
            conn.subscribe(
                LafManagerListener.TOPIC,
                LafManagerListener { trySend(Unit) }
            )
            awaitClose { conn.disconnect() }
        }.onEach {
            withContext(Dispatchers.AppUI) {
                updateLaf()
            }
        }.launchIn(project.lifecycleScope)

        updateLaf()
    }

    fun setIsBusy(isBusy: Boolean) {
        searchTextField.isEnabled = !isBusy
        searchTextField.updateAndRepaint()

        headerPanel.showBusyIndicator(isBusy)
        headerPanel.updateAndRepaint()
    }

    private fun updateListEmptyState(targetModules: TargetModules) {
        when {
            isSearching() -> {
                listPanel.emptyText.text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.empty.searching")
            }
            targetModules is TargetModules.None -> {
                listPanel.emptyText.text = PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.empty.noModule")
            }
            else -> {
                val targetModuleNames = when (targetModules) {
                    is TargetModules.All -> PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.empty.allModules")
                    is TargetModules.One -> targetModules.module.projectModule.name
                    is TargetModules.None -> throw IllegalStateException("No module selected empty state should be handled separately")
                }
                listPanel.emptyText.text =
                    PackageSearchBundle.message("packagesearch.ui.toolwindow.packages.empty.packagesOnly", targetModuleNames)
            }
        }
    }

    private fun isSearching() = !searchTextField.text.isNullOrBlank()

    internal data class ViewModel(
        val headerData: PackagesHeaderData,
        val packageModels: List<PackageModel>,
        val filterOptions: FilterOptions,
        val targetModules: TargetModules,
        val knownRepositoriesInTargetModules: KnownRepositories.InTargetModules,
        val allKnownRepositories: KnownRepositories.All,
        val tableData: List<PackagesTableItem<*>>,
        val traceInfo: TraceInfo
    )

    override suspend fun display(viewModel: ViewModel) = withContext(Dispatchers.AppUI) {

        onlyStableCheckBox.isSelected = viewModel.filterOptions.onlyStable
        onlyKotlinMpCheckBox.isSelected = viewModel.filterOptions.onlyKotlinMultiplatform

        updateListEmptyState(viewModel.targetModules)

        logDebug(viewModel.traceInfo, "PackagesListPanel#display()") { "PackagesListPanel#display() — Got new data" }

        headerPanel.display(
            HeaderPanel.ViewModel(
                viewModel.headerData.labelText,
                viewModel.headerData.count,
                viewModel.headerData.availableUpdatesCount,
                viewModel.headerData.updateOperations
            )
        )

        packagesTable.display(
            PackagesTable.ViewModel(
                viewModel.tableData,
                viewModel.filterOptions.onlyStable,
                viewModel.targetModules,
                viewModel.knownRepositoriesInTargetModules,
                viewModel.allKnownRepositories,
                viewModel.traceInfo
            )
        )

        tableScrollPane.isVisible = viewModel.packageModels.isNotEmpty()
        listPanel.updateAndRepaint()

        packagesTable.updateAndRepaint()
        packagesPanel.updateAndRepaint()
    }

    private fun registerForUiEvents() {
        packagesTable.transferFocusUp = {
            IdeFocusManager.getInstance(project).requestFocus(searchTextField, false)
        }

        searchTextField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                AppUIExecutor.onUiThread().execute { searchClient.setSearchQuery(searchTextField.text) }
            }
        })

        onlyStableCheckBox.addItemListener { e ->
            val selected = e.stateChange == ItemEvent.SELECTED
            searchClient.setOnlyStable(selected)
            PackageSearchEventsLogger.logToggle(FUSGroupIds.ToggleTypes.OnlyStable, selected)
        }

        onlyKotlinMpCheckBox.addItemListener { e ->
            val selected = e.stateChange == ItemEvent.SELECTED
            searchClient.setOnlyKotlinMultiplatform(selected)
            PackageSearchEventsLogger.logToggle(FUSGroupIds.ToggleTypes.OnlyKotlinMp, selected)
        }
    }

    private fun updateLaf() {
        @Suppress("MagicNumber") // Dimension constants
        with(searchTextField) {
            textEditor.putClientProperty("JTextField.Search.Gap", 6.scaled())
            textEditor.putClientProperty("JTextField.Search.GapEmptyText", (-1).scaled())
            textEditor.border = scaledEmptyBorder(left = 6)
            textEditor.isOpaque = true
            textEditor.background = PackageSearchUI.HeaderBackgroundColor
        }
    }

    override fun build() = PackageSearchUI.boxPanel {
        add(searchPanel)
        add(headerPanel)
        add(listPanel)

        @Suppress("MagicNumber") // Dimension constants
        minimumSize = Dimension(200.scaled(), minimumSize.height)
    }
}
