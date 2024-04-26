// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.observable.util.addMouseHoverListener
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.hover.HoverListener
import com.intellij.util.text.nullize
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.sdk.PythonSdkType
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.Cursor
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.ComboBoxEditor
import javax.swing.JComponent
import javax.swing.JLabel

class PythonSdkComboBoxWithBrowseButtonEditor(val comboBox: ComboBox<Sdk?>,
                                              val presenter: PythonAddInterpreterPresenter,
                                              onPathSelected: (String) -> Unit) : ComboBoxEditor {
  private val component = SimpleColoredComponent()
  private val panel: JComponent
  private lateinit var iconLabel: JLabel
  private var _item: Any? = null
  private var isBusy = false

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
                if (!isBusy) {
                  (component as JLabel).let {
                    icon = AllIcons.General.OpenDiskHover
                    background = JBColor.namedColor("ComboBox.nonEditableBackground")
                  }
                  cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                }
              }

              override fun mouseMoved(component: Component, x: Int, y: Int) {}

              override fun mouseExited(component: Component) {
                if (!isBusy) {
                  (component as JLabel).let {
                    icon = AllIcons.General.OpenDisk
                    background = defaultBackground
                  }
                  cursor = Cursor.getDefaultCursor()
                }
              }
            })

            addMouseListener(object : MouseAdapter() {
              override fun mouseClicked(e: MouseEvent?) {
                if (!isBusy) {
                  val currentBaseSdkVirtualFile = (_item as? Sdk)?.let { sdk ->
                    val currentBaseSdkPathOnTarget = sdk.homePath.nullize(nullizeSpaces = true)
                    currentBaseSdkPathOnTarget?.let { presenter.tryGetVirtualFile(it) }
                  }

                  FileChooser.chooseFile(PythonSdkType.getInstance().homeChooserDescriptor,
                                         null,
                                         currentBaseSdkVirtualFile) { file ->
                    val nioPath = file?.toNioPath() ?: return@chooseFile
                    val targetPath = presenter.getPathOnTarget(nioPath)
                    comboBox.setPathToSelectAfterModelUpdate(targetPath)
                    onPathSelected(targetPath)
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
    presenter.scope.launch(start = CoroutineStart.UNDISPATCHED) {
      presenter.detectingSdks.collectLatest {
        withContext(presenter.uiContext) {
          setBusy(it)
        }
      }
    }
  }


  override fun setItem(anObject: Any?) {
    if (_item == anObject) return
    _item = anObject
    component.clear()
    if (anObject is Sdk) component.customizeForPythonSdk(anObject)
  }

  private fun setBusy(busy: Boolean) {
    isBusy = busy
    iconLabel.icon = if (isBusy) AnimatedIcon.Default.INSTANCE else AllIcons.General.OpenDisk
    component.isEnabled = !isBusy
    comboBox.isEnabled = !isBusy
  }

  override fun getEditorComponent(): Component = panel
  override fun getItem(): Any? = _item

  override fun selectAll() {}
  override fun addActionListener(l: ActionListener?) {}
  override fun removeActionListener(l: ActionListener?) {}
}