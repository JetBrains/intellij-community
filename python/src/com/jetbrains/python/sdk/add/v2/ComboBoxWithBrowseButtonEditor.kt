// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.icons.AllIcons
import com.intellij.openapi.observable.util.addMouseHoverListener
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
import com.intellij.ui.util.preferredHeight
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.pathValidation.PlatformAndRoot.Companion.getPlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateExecutableFile
import com.jetbrains.python.ui.pyModalBlocking
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.ComboBoxEditor
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.io.path.pathString

@VisibleForTesting
class ComboBoxWithBrowseButtonEditor<P : PathHolder>(
  val comboBox: ComboBox<PythonSelectableInterpreter<P>?>,
  val fileSystem: FileSystem<P>,
  val browseTitle: @NlsContexts.DialogTitle String,
  onPathSelected: (String) -> Unit,
) : ComboBoxEditor {
  private val component = SimpleColoredComponent()
  private val panel: JComponent
  private lateinit var iconLabel: JLabel
  private var _item: Any? = null
  var isBusy: Boolean = false
    private set

  @VisibleForTesting
  val fieldAccessor: TextComponentAccessor<ComboBox<PythonSelectableInterpreter<P>?>> = object : TextComponentAccessor<ComboBox<PythonSelectableInterpreter<P>?>> {
    override fun getText(component: ComboBox<PythonSelectableInterpreter<P>?>): @NlsSafe String? =
      when (val p = component.getItemAt(component.selectedIndex)?.homePath ?: return null) {
        is PathHolder.Eel -> p.path.pathString
        is PathHolder.Target -> p.pathString
      }

    override fun setText(component: ComboBox<PythonSelectableInterpreter<P>?>, text: @NlsSafe String) {
      onPathSelected(text)
    }
  }

  init {
    panel = panel {
      row {
        cell(component)
          .customize(UnscaledGaps(0))
          .resizableColumn()
          .applyToComponent {
            border = BorderFactory.createEmptyBorder()
            minimumSize = JBUI.emptySize()
          }

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
                fileSystem.configureFileBrowseEditor(fieldAccessor, comboBox, browseTitle, panel)
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
    component.customizeForPythonInterpreter(isBusy, anObject as? PythonSelectableInterpreter<*>)
  }

  fun setBusy(busy: Boolean) {
    isBusy = busy
    iconLabel.icon = if (isBusy) AnimatedIcon.Default.INSTANCE else AllIcons.General.OpenDisk
    component.isEnabled = !isBusy
    comboBox.isEnabled = !isBusy
    component.clear()
    (item as? PythonSelectableInterpreter<*>).takeIf { !busy }.let {
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
  private val width: Int,
  private val targetEnvironmentConfiguration: TargetEnvironmentConfiguration,
) : DialogWrapper(null) {

  var path: String = ""
    private set

  init {
    this.title = title
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
    }.also {
      it.preferredSize = Dimension(width, it.preferredHeight)
    }
  }
}