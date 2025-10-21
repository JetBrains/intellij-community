// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.BrowsableTargetEnvironmentType
import com.intellij.execution.target.TargetBrowserHints
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.getTargetType
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.util.addMouseHoverListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.hover.HoverListener
import com.intellij.util.SlowOperations
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.pathValidation.PlatformAndRoot.Companion.getPlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateExecutableFile
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.ui.pyModalBlocking
import java.awt.Component
import java.awt.Cursor
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.ComboBoxEditor
import javax.swing.JComponent
import javax.swing.JLabel

internal class ComboBoxWithBrowseButtonEditor<T, P : PathHolder>(
  val comboBox: ComboBox<T?>,
  val fileSystem: FileSystem<P>,
  val browseTitle: @NlsContexts.DialogTitle String,
  onPathSelected: (String) -> Unit,
) : ComboBoxEditor {
  private val component = SimpleColoredComponent()
  private val panel: JComponent
  private lateinit var iconLabel: JLabel
  private var _item: Any? = null
  var isBusy = false
    private set

  private val fieldAccessor = object : TextComponentAccessor<ComboBox<T?>> {
    override fun getText(component: ComboBox<T?>): @NlsSafe String? {
      return component.selectedItem?.toString()
    }

    override fun setText(component: ComboBox<T?>?, text: @NlsSafe String) {
      onPathSelected(text)
    }
  }

  init {
    panel = panel {
      row {
        cell(component)
          .customize(UnscaledGaps(0))
          .resizableColumn()
          .applyToComponent { border = BorderFactory.createEmptyBorder() }

        iconLabel = cell(JLabel(AllIcons.General.OpenDisk))
          .customize(UnscaledGaps(0))
          .applyToComponent {
            isOpaque = true
            toolTipText = message("sdk.create.tooltip.browse")
            addMouseHoverListener(null, object : HoverListener() {
              val defaultBackground = this@applyToComponent.background

              override fun mouseEntered(component: Component, x: Int, y: Int) {
                if (isBusy) return

                (component as JLabel).let {
                  icon = AllIcons.General.OpenDiskHover
                  background = JBColor.namedColor("ComboBox.nonEditableBackground")
                }
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
              }

              override fun mouseMoved(component: Component, x: Int, y: Int) {}

              override fun mouseExited(component: Component) {
                if (isBusy) return

                (component as JLabel).let {
                  icon = AllIcons.General.OpenDisk
                  background = defaultBackground
                }
                cursor = Cursor.getDefaultCursor()
              }
            })


            addMouseListener(object : MouseAdapter() {
              override fun mouseClicked(e: MouseEvent?) {
                if (isBusy) return

                when (fileSystem) {
                  is FileSystem.Eel -> {
                    SlowOperations.knownIssue("PY-666").use { // TODO FIX ME PLEASE if you know how
                      val descriptor = PythonSdkType.getInstance().homeChooserDescriptor.withTitle(browseTitle)
                      FileChooser.chooseFile(descriptor, null, parent, null) { file ->
                        val path = file?.toNioPath()
                        path?.toString()?.let {
                          fieldAccessor.setText(comboBox, it)
                        }
                      }
                    }
                  }
                  is FileSystem.Target -> {
                    val targetType = fileSystem.targetEnvironmentConfiguration.getTargetType()
                    if (targetType is BrowsableTargetEnvironmentType) {
                      val descriptor = FileChooserDescriptorFactory.singleFile().withTitle(browseTitle)
                      val hints = TargetBrowserHints(showLocalFsInBrowser = true, descriptor)

                      val actionListener = targetType.createBrowser(
                        ProjectManager.getInstance().defaultProject,
                        hints.customFileChooserDescriptor!!.title,
                        fieldAccessor,
                        comboBox,
                        { fileSystem.targetEnvironmentConfiguration },
                        hints
                      )
                      actionListener.actionPerformed(null)
                    }
                    else {
                      val dialog = ManualPathEntryDialog(browseTitle, panel.width, fileSystem.targetEnvironmentConfiguration)
                      if (dialog.showAndGet()) {
                        fieldAccessor.setText(comboBox, dialog.path)
                      }
                    }
                  }
                }
              }
            })
          }
          .align(AlignX.RIGHT)
          .component
      }
    }

    panel.border = null
  }


  override fun setItem(anObject: Any?) {
    if (_item == anObject) return
    _item = anObject
    component.clear()
    component.customizeForPythonInterpreter(isBusy, anObject as? PythonSelectableInterpreter<P>)
  }

  fun setBusy(busy: Boolean) {
    isBusy = busy
    iconLabel.icon = if (isBusy) AnimatedIcon.Default.INSTANCE else AllIcons.General.OpenDisk
    component.isEnabled = !isBusy
    comboBox.isEnabled = !isBusy
    component.clear()
    (item as? PythonSelectableInterpreter<P>).takeIf { !busy }.let {
      component.customizeForPythonInterpreter(busy, it)
    }
  }

  override fun getEditorComponent(): Component = panel
  override fun getItem(): Any? = _item

  override fun selectAll() {}
  override fun addActionListener(l: ActionListener?) {}
  override fun removeActionListener(l: ActionListener?) {}
}

class ManualPathEntryDialog(
  title: @NlsContexts.DialogTitle String,
  width: Int,
  val targetEnvironmentConfiguration: TargetEnvironmentConfiguration,
) : DialogWrapper(null) {

  var path: String = ""
    private set

  init {
    this.title = title
    setSize(width, size.height)
    isResizable = false
    init()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row(message("path.label")) {
        textField()
          .align(AlignX.FILL)
          .bindText(::path)
          .validationOnApply { textField ->
            val text = textField.text
            return@validationOnApply pyModalBlocking {
              // this dialog is always for remote
              validateExecutableFile(
                ValidationRequest(text, platformAndRoot = targetEnvironmentConfiguration.getPlatformAndRoot(defaultIsLocal = false)))
            }
          }
          .focused()
      }
    }
  }
}