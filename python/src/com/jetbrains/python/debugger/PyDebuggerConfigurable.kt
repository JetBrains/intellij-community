// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.options.BackedByPersistentState
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.TooltipWithClickableLinks
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.SegmentedButton
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonRuntimeService
import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JEditorPane

private const val EVALUATION_TIMEOUT_STEP_MS = 500
private const val PORT_MIN = 0
private const val PORT_MAX = 65535

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

  private lateinit var myBackendSelector: SegmentedButtonComponent<PyDebuggerBackend>
  private lateinit var myBackendDescription: JEditorPane

  private var myMainPanel: DialogPanel? = null

  override fun getDisplayName(): String = PyBundle.message("configurable.PyDebuggerConfigurable.display.name")

  override fun getHelpTopic(): String = "reference.idesettings.debugger.python"

  override fun getId(): String = getHelpTopic()

  override fun createComponent(): JComponent {
    if (myMainPanel == null) myMainPanel = createPanel()
    return myMainPanel!!
  }

  private fun createPanel(): DialogPanel {
    val settings = PyDebuggerOptionsProvider.getInstance(myProject)
    val warningIcon = JBLabel(AllIcons.General.BalloonWarning)
    IdeTooltipManager.getInstance().setCustomTooltip(
      warningIcon,
      TooltipWithClickableLinks.ForBrowser(warningIcon, PyBundle.message("debugger.warning.message")))

    val runInServerMode = JBCheckBox(PyBundle.message("form.debugger.run.debugger.in.server.mode"))
    val debuggerPort = JBIntSpinner(settings.debuggerPort, PORT_MIN, PORT_MAX)
    val evaluationTimeout = JBIntSpinner(settings.evaluationResponseTimeout, 0, Int.MAX_VALUE, EVALUATION_TIMEOUT_STEP_MS)
    initBackendPanel()

    return panel {
      row {
        checkBox(PyBundle.message("form.debugger.attach.to.subprocess.automatically.while.debugging"))
          .bindSelected({ settings.isAttachToSubprocess }, { settings.isAttachToSubprocess = it })
      }
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
      }
      row {
        checkBox(PyBundle.message("form.debugger.gevent.compatible"))
          .bindSelected({ settings.isSupportGeventDebugging }, { settings.isSupportGeventDebugging = it })
      }
      row {
        checkBox(PyBundle.message("form.debugger.drop.into.debugger.on.failed.tests"))
          .bindSelected({ settings.isDropIntoDebuggerOnFailedTest }, { settings.isDropIntoDebuggerOnFailedTest = it })
      }
      row {
        val qtCell = checkBox(PyBundle.message("form.debugger.pyqt.compatible"))
          .bindSelected({ settings.isSupportQtDebugging }, { settings.isSupportQtDebugging = it })
        comboBox(DefaultComboBoxModel(PyQtBackend.entries.toTypedArray()))
          .enabledIf(qtCell.component.selected)
          .bindItem(
            getter = { PyQtBackend.valueOf(settings.getPyQtBackend().uppercase()) },
            setter = { it?.let { settings.pyQtBackend = it.name.lowercase() } }
          )
      }
      if (Registry.`is`("python.debug.use.single.port")) {
        row { cell(runInServerMode).bindSelected({ settings.isRunDebuggerInServerMode }, { settings.isRunDebuggerInServerMode = it }) }
        indent {
          row {
            label(PyBundle.message("form.debugger.debugger.port"))
            cell(debuggerPort)
              .onApply { settings.debuggerPort = debuggerPort.number }
              .onReset { debuggerPort.number = settings.debuggerPort }
              .onIsModified { debuggerPort.number != settings.debuggerPort }
          }.visibleIf(runInServerMode.selected)
        }
      }
      row(PyBundle.message("debugger.attach.to.process.filter.names")) {
        textField().align(AlignX.FILL).bindText({ settings.attachProcessFilter }, { settings.attachProcessFilter = it })
      }
      row(PyBundle.message("form.debugger.response.timeout")) {
        cell(evaluationTimeout)
          .onApply { settings.evaluationResponseTimeout = evaluationTimeout.number }
          .onReset { evaluationTimeout.number = settings.evaluationResponseTimeout }
          .onIsModified { evaluationTimeout.number != settings.evaluationResponseTimeout }
      }
      row {
        label(PyBundle.message("debugger.backend.switcher.label"))
        cell(myBackendSelector)
      }
      row {
        myBackendDescription = text(getBackendDescription(settings.selectedBackend))
          .applyToComponent { foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND }
          .component
      }
    }.also {
      myBackendSelector.addChangeListener {
        myBackendSelector.selectedItem?.let { backend ->
          myBackendDescription.text = getBackendDescription(backend)
        }
      }
    }
  }

  private fun initBackendPanel() {
    val projectSdk = ProjectRootManager.getInstance(myProject).getProjectSdk()
    val debugpyDisabled = projectSdk != null &&
                          PythonRuntimeService.getInstance().getLanguageLevelForSdk(projectSdk)
                            .isOlderThan(LanguageLevel.PYTHON39)
    myBackendSelector = SegmentedButtonComponent { backend ->
      @Suppress("HardCodedStringLiteral") // debugpy and pydevd should not be i18.
      val name = if (backend == PyDebuggerBackend.DEBUGPY) "debugpy" else "pydevd"
      val enabled = backend != PyDebuggerBackend.DEBUGPY || !debugpyDisabled
      val tooltip = if (backend == PyDebuggerBackend.DEBUGPY && debugpyDisabled)
        PyBundle.message("debugger.backend.debugpy.disabled.tooltip")
      else null
      SegmentedButton.createPresentation(name, tooltip, null, enabled)
    }
    myBackendSelector.items = PyDebuggerBackend.entries
    myBackendSelector.spacing = IntelliJSpacingConfiguration()
  }

  private fun applyBackend() {
    val newBackend = myBackendSelector.selectedItem ?: PyDebuggerBackend.PYDEVD
    PyDebuggerOptionsProvider.switchBackendWithRestart(myProject, newBackend)
  }

  private fun isBackendModified(): Boolean {
    val settings = PyDebuggerOptionsProvider.getInstance(myProject)
    return myBackendSelector.selectedItem != settings.selectedBackend
  }

  override fun isModified(): Boolean {
    return myMainPanel?.isModified() == true ||
           isBackendModified()
  }

  override fun apply() {
    myMainPanel?.apply()
    if (isBackendModified()) {
      applyBackend()
    }
  }

  override fun reset() {
    myMainPanel?.reset()
    myBackendSelector.selectedItem = PyDebuggerOptionsProvider.getInstance(myProject).selectedBackend
  }

  private fun getBackendDescription(backend: PyDebuggerBackend): @Nls String =
    when (backend) {
      PyDebuggerBackend.PYDEVD -> PyBundle.message("debugger.backend.pydevd.description")
      PyDebuggerBackend.DEBUGPY -> PyBundle.message("debugger.backend.debugpy.description")
    }
}