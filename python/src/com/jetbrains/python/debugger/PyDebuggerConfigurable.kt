// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeTooltipManager
import com.intellij.ide.setToolTipText
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.options.BackedByPersistentState
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.TooltipWithClickableLinks
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.COLUMNS_SHORT
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

private const val EVALUATION_TIMEOUT_STEP_MS = 500

class PyDebuggerConfigurable(private val myProject: Project) : SearchableConfigurable, Configurable.NoScroll, BackedByPersistentState {

  @ApiStatus.Internal
  override fun getBackingComponents(): Collection<PersistentStateComponent<*>> =
    listOf(PyDebuggerOptionsProvider.getInstance(myProject))

  private enum class PyQtBackend(private val displayNameSupplier: () -> @Nls String) {
    AUTO({ PyBundle.message("python.debugger.qt.backend.auto") }),
    PYQT4({ "PyQt4" }),
    PYQT5({ "PyQt5" }),
    PYQT6({ "PyQt6" }),
    PYSIDE({ "PySide" }),
    PYSIDE2({ "PySide2" }),
    PYSIDE6({ "PySide6" });

    override fun toString(): String = displayNameSupplier()
  }

  private var mySelectedBackend: PyDebuggerBackend = PyDebuggerBackend.PYDEVD

  private var myMainPanel: DialogPanel? = null

  override fun getDisplayName(): String = PyBundle.message("configurable.PyDebuggerConfigurable.display.name")

  override fun getHelpTopic(): String = "reference.idesettings.debugger.python"

  override fun getId(): String = getHelpTopic()

  override fun createComponent(): JComponent {
    if (myMainPanel == null) myMainPanel = createPanel()
    return myMainPanel!!
  }

  // "pydevd" and "debugpy" are product names. They keep the lowercase spelling and stay untranslated.
  @Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")
  private fun createPanel(): DialogPanel {
    val settings = PyDebuggerOptionsProvider.getInstance(myProject)
    val evaluationTimeout = JBIntSpinner(settings.evaluationResponseTimeout, 0, Int.MAX_VALUE, EVALUATION_TIMEOUT_STEP_MS)
    val debugpyDisabled = !isDebugpyAvailable()
    val warningIcon = JBLabel(AllIcons.General.BalloonWarning)
    IdeTooltipManager.getInstance().setCustomTooltip(
      warningIcon,
      TooltipWithClickableLinks.ForBrowser(warningIcon, PyBundle.message("debugger.warning.message")))

    lateinit var pydevdButton: JBRadioButton
    lateinit var debugpyButton: JBRadioButton

    return panel {
      buttonsGroup {
        row(PyBundle.message("debugger.backend.settings.label")) {
          pydevdButton = radioButton("pydevd", PyDebuggerBackend.PYDEVD).component
          debugpyButton = radioButton("debugpy", PyDebuggerBackend.DEBUGPY)
            .enabled(!debugpyDisabled)
            .applyToComponent {
              if (debugpyDisabled) {
                setToolTipText(HtmlChunk.text(debugpyDisabledMessage(myProject)))
              }
            }
            .component
        }
          // Every labeled row on this page keeps its own grid. A shared label column is as wide as the longest
          // label, which pushes the short rows far to the right.
          .layout(RowLayout.INDEPENDENT)
      }.bind({ mySelectedBackend }, { mySelectedBackend = it })

      row {
        text(PyBundle.message("debugger.backend.pydevd.description"))
          .applyToComponent { foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND }
      }.visibleIf(pydevdButton.selected)
      row {
        text(PyBundle.message("debugger.backend.debugpy.description"))
          .applyToComponent { foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND }
      }.visibleIf(debugpyButton.selected)

      row {
        checkBox(PyBundle.message("form.debugger.attach.to.subprocess.automatically.while.debugging"))
          .bindSelected({ settings.isAttachToSubprocess }, { settings.isAttachToSubprocess = it })
      }
      // Signature collection is a pydevd protocol feature. The DAP protocol carries no signatures,
      // so the setting is offered under pydevd only.
      row {
        checkBox(PyBundle.message("form.debugger.collect.run.time.types.information.for.code.insight"))
          .bindSelected({ settings.isSaveCallSignatures }, { settings.isSaveCallSignatures = it })
        cell(warningIcon)
        link(PyBundle.message("form.debugger.clear.caches")) {
          val cleared = PySignatureCacheManager.getInstance(myProject).clearCache()
          val msg = if (cleared) PyBundle.message("python.debugger.collection.signatures.deleted")
          else PyBundle.message("python.debugger.nothing.to.delete")
          Messages.showInfoMessage(myProject, msg, PyBundle.message("debugger.delete.signature.cache"))
        }
      }.visibleIf(pydevdButton.selected)
      // Gevent support never worked with pydevd, so the setting is offered for debugpy only. See PY-90935.
      row {
        checkBox(PyBundle.message("form.debugger.gevent.compatible"))
          .bindSelected({ settings.isSupportGeventDebugging }, { settings.isSupportGeventDebugging = it })
      }.visibleIf(debugpyButton.selected)
      row {
        checkBox(PyBundle.message("form.debugger.drop.into.debugger.on.failed.tests"))
          .bindSelected({ settings.isDropIntoDebuggerOnFailedTest }, { settings.isDropIntoDebuggerOnFailedTest = it })
      }
      // debugpy handles Qt event loops itself, so these two settings apply to pydevd only.
      row {
        val qtCell = checkBox(PyBundle.message("form.debugger.pyqt.compatible"))
          .bindSelected({ settings.isSupportQtDebugging }, { settings.isSupportQtDebugging = it })
        comboBox(DefaultComboBoxModel(PyQtBackend.entries.toTypedArray()))
          .enabledIf(qtCell.component.selected)
          .bindItem(
            getter = { PyQtBackend.valueOf(settings.getPyQtBackend().uppercase()) },
            setter = { it?.let { settings.pyQtBackend = it.name.lowercase() } }
          )
      }.visibleIf(pydevdButton.selected)
      row(PyBundle.message("debugger.attach.to.process.filter.names")) {
        textField().columns(COLUMNS_SHORT).bindText({ settings.attachProcessFilter }, { settings.attachProcessFilter = it })
      }.layout(RowLayout.INDEPENDENT)
      row(PyBundle.message("form.debugger.response.timeout")) {
        cell(evaluationTimeout)
          .onApply { settings.evaluationResponseTimeout = evaluationTimeout.number }
          .onReset { evaluationTimeout.number = settings.evaluationResponseTimeout }
          .onIsModified { evaluationTimeout.number != settings.evaluationResponseTimeout }
      }.layout(RowLayout.INDEPENDENT).visibleIf(pydevdButton.selected)
    }
  }

  /**
   * Whether the debugpy radio button can be selected.
   *
   * The per-SDK rule lives behind [PyDebuggerBackendSwitchHandler], so the settings page and the backend
   * switcher apply the same checks. [isDebugpyAvailableInProject] is not used here: it scans modules inside
   * `runBlockingCancellable`, which is forbidden on the EDT, and this panel is built on the EDT.
   */
  private fun isDebugpyAvailable(): Boolean {
    if (!isPythonDapPluginInstalledAndEnabled()) return false
    val sdk = getEffectiveSdk(myProject) ?: return true
    return isDebugpyAvailableForSdk(sdk, myProject)
  }

  override fun isModified(): Boolean = myMainPanel?.isModified() == true

  override fun apply() {
    myMainPanel?.apply()
    if (mySelectedBackend != PyDebuggerOptionsProvider.getInstance(myProject).selectedBackend) {
      PyDebuggerOptionsProvider.switchBackendWithRestart(myProject, mySelectedBackend)
    }
  }

  override fun reset() {
    mySelectedBackend = PyDebuggerOptionsProvider.getInstance(myProject).selectedBackend
    myMainPanel?.reset()
  }
}
