// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBCardLayout
import com.intellij.ui.components.DialogPanel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.DialogAction
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.add.PyAddSdkDialog.Companion.show
import com.jetbrains.python.sdk.collectAddInterpreterActions
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.CardLayout
import java.util.function.Consumer
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import kotlin.coroutines.cancellation.CancellationException

/**
 * The dialog may look like the normal dialog with OK, Cancel and Help buttons
 * or the wizard dialog with Next, Previous, Finish, Cancel and Help buttons.
 *
 * Use [show] to instantiate and show the dialog.
 *
 */
class PyAddSdkDialog private constructor(
  private val project: Project,
  private val module: Module?,
  private val sdkAddedCallback: Consumer<Sdk>,
) : DialogWrapper(project) {

  private val dialogActions: List<DialogAction>

  init {
    title = PyBundle.message("python.sdk.add.python.interpreter.title")

    val moduleOrProject = module?.let { ModuleOrProject.ModuleAndProject(it) } ?: ModuleOrProject.ProjectOnly(project)
    dialogActions = collectAddInterpreterActions(moduleOrProject) {
      sdkAddedCallback.accept(it)
    }

    init()
  }

  override fun createCenterPanel(): JComponent {
    val mainPanel = DialogPanel(null, JBCardLayout())
    mainPanel.add(SPLITTER_COMPONENT_CARD_PANE, createCardSplitter())
    return mainPanel
  }

  override fun createSouthPanel(): JComponent = object : JComponent() {}

  override fun postponeValidation(): Boolean = false

  override fun doValidateAll(): List<ValidationInfo> = emptyList()


  private data class DialogCard(
    val title: @Nls(capitalization = Nls.Capitalization.Title) String,
    val icon: Icon,
    val dialog: DialogWrapper,
  )

  private fun buildDialogCards(): List<DialogCard> {
    val cards = dialogActions.mapNotNull { action ->
      val dialogWrapper = try {
        action.createDialog()
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Exception) { // skip broken extensions like Vagrant
        thisLogger().error(e)
        null
      }

      dialogWrapper?.let {
        DialogCard(action.target, action.icon, it)
      }
    }

    cards.forEach { panel ->
      Disposer.register(disposable, panel.dialog.disposable)
      Disposer.register(panel.dialog.disposable, Disposable {
        close(panel.dialog.exitCode)
      })
    }

    return cards
  }

  private fun createCardSplitter(): Splitter {
    val cards = buildDialogCards()

    val cardLayout = CardLayout()
    val dialogCardPanel = JPanel(cardLayout).apply {
      for (card in cards) {
        add(card.dialog.contentPane, card.title)
      }
    }

    val cardSelectionPanel = JPanel(BorderLayout()).apply {
      border = BorderFactory.createEmptyBorder(0, 8, 0, 12)
      JComboBox(cards.toTypedArray()).apply {
        addActionListener {
          cardLayout.show(dialogCardPanel, (selectedItem as DialogCard).title)
        }
        renderer = TargetComboBoxListCellRenderer()
        toolTipText = PyBundle.message("python.configuration.choose.target.to.run")
        isFocusable = false
      }.let { targetComboBox ->
        add(targetComboBox, BorderLayout.NORTH)
      }
    }

    return Splitter(true, 0.01f).apply {
      dividerPositionStrategy = Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE
      firstComponent = cardSelectionPanel
      secondComponent = dialogCardPanel
    }
  }

  private class TargetComboBoxListCellRenderer : ColoredListCellRenderer<DialogCard>() {
    override fun customizeCellRenderer(list: JList<out DialogCard>, value: DialogCard, index: Int, selected: Boolean, hasFocus: Boolean) {
      icon = value.icon
      append(value.title)
    }
  }

  companion object {
    private const val SPLITTER_COMPONENT_CARD_PANE = "Splitter"

    @JvmStatic
    @ApiStatus.Internal
    fun show(project: Project, module: Module?, sdkAddedCallback: Consumer<Sdk>) {
      val dialog = PyAddSdkDialog(project = project, module = module, sdkAddedCallback)
      dialog.show()
    }
  }
}