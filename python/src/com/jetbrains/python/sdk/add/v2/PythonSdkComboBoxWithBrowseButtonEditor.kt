// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.observable.util.addMouseHoverListener
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.hover.HoverListener
import com.intellij.util.SlowOperations
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.sdk.PythonSdkType
import java.awt.Component
import java.awt.Cursor
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.ComboBoxEditor
import javax.swing.JComponent
import javax.swing.JLabel

internal class PythonSdkComboBoxWithBrowseButtonEditor(
  val comboBox: ComboBox<PythonSelectableInterpreter?>,
  val controller: PythonAddInterpreterModel,
  onPathSelected: (String) -> Unit,
) : ComboBoxEditor {
  private val component = SimpleColoredComponent()
  private val panel: JComponent
  private lateinit var iconLabel: JLabel
  private var _item: Any? = null
  var isBusy = false
    private set

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

                SlowOperations.knownIssue("PY-666").use { // TODO FIX ME PLEASE if you know how
                  FileChooser.chooseFile(PythonSdkType.getInstance().homeChooserDescriptor, null, parent, null) { file ->
                    val path = file?.toNioPath()
                    path?.toString()?.let {
                      onPathSelected(it)
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
    component.customizeForPythonInterpreter(controller.interpreterLoading.value, anObject as? PythonSelectableInterpreter)
  }

  fun setBusy(busy: Boolean) {
    isBusy = busy
    iconLabel.icon = if (isBusy) AnimatedIcon.Default.INSTANCE else AllIcons.General.OpenDisk
    component.isEnabled = !isBusy
    comboBox.isEnabled = !isBusy
    component.clear()
    (item as? PythonSelectableInterpreter).takeIf { !busy }.let {
      component.customizeForPythonInterpreter(controller.interpreterLoading.value, it)
    }
  }

  override fun getEditorComponent(): Component = panel
  override fun getItem(): Any? = _item

  override fun selectAll() {}
  override fun addActionListener(l: ActionListener?) {}
  override fun removeActionListener(l: ActionListener?) {}
}