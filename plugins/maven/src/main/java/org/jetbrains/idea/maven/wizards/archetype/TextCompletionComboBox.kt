// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards.archetype

import com.intellij.codeInsight.lookup.impl.LookupCellRenderer
import com.intellij.codeInsight.lookup.impl.LookupCellRenderer.REGULAR_MATCHED_ATTRIBUTES
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionPopup.UpdatePopupType
import com.intellij.openapi.observable.properties.AtomicObservableProperty
import com.intellij.openapi.observable.properties.transform
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.observable.util.onceWhenFocusGained
import com.intellij.openapi.observable.util.whenFocusGained
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.addKeyboardAction
import com.intellij.openapi.ui.collectionModel
import com.intellij.openapi.ui.getKeyStrokes
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.*
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import org.jetbrains.idea.maven.wizards.archetype.TextCompletionComboBoxRenderer.Companion.append
import javax.swing.JList
import javax.swing.JTextField
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.plaf.basic.BasicComboBoxEditor

class TextCompletionComboBox<T>(
  private val converter: TextCompletionComboBoxConverter<T>,
  renderer: TextCompletionComboBoxRenderer<T>
) : ComboBox<T>(CollectionComboBoxModel()) {

  constructor(converter: TextCompletionComboBoxConverter<T>) : this(converter, DefaultRenderer())

  private fun findOrCreateItem(text: String): T {
    val item = converter.createItem(text)
    val existedItem = collectionModel.items.find { it == item }
    return existedItem ?: item
  }

  private fun createEditor(): Editor<T> {
    val property = AtomicObservableProperty("")
    val editor = object : Editor<T>() {
      override fun setItem(anObject: Any?) {}
      override fun getItem(): Any? = selectedItem
    }
    editor.textField.bind(property)
    bind(property.transform(::findOrCreateItem, converter::createString))
    return editor
  }

  private fun updatePopup(type: UpdatePopupType) {
    when (type) {
      UpdatePopupType.SHOW_IF_HAS_VARIANCES ->
        if (collectionModel.items.isNotEmpty())
          showPopup()
      UpdatePopupType.SHOW ->
        showPopup()
      UpdatePopupType.HIDE ->
        hidePopup()
      UpdatePopupType.UPDATE -> {}
    }
  }

  init {
    val editor = createEditor()
    val cellRenderer = Renderer(renderer, editor)

    setRenderer(wrapWithExpandedRenderer(cellRenderer))
    setEditor(editor)
    setEditable(true)

    editor.textField.whenFocusGained {
      if (editor.text.isEmpty()) {
        updatePopup(UpdatePopupType.SHOW_IF_HAS_VARIANCES)
      }
    }
    editor.textField.onceWhenFocusGained {
      updatePopup(UpdatePopupType.SHOW_IF_HAS_VARIANCES)
    }
    editor.textField.addKeyboardAction(getKeyStrokes(IdeActions.ACTION_CODE_COMPLETION)) {
      updatePopup(UpdatePopupType.SHOW)
    }
  }

  init {
    popup?.list?.apply {
      prototypeCellValue = converter.createItem("X")
      selectionBackground = LookupCellRenderer.SELECTED_BACKGROUND_COLOR
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      isFocusable = false
    }
  }

  private fun wrapWithExpandedRenderer(renderer: ListCellRenderer<in T>): ListCellRenderer<in T> {
    val list = popup?.list ?: return renderer
    val handler = ExpandableItemsHandlerFactory.install<Int>(list)
    return ExpandedItemListCellRendererWrapper(renderer, handler)
  }

  private abstract class Editor<T> : BasicComboBoxEditor() {
    val textField: JTextField get() = editor
    val text: String get() = editor.text
  }

  private class Renderer<T>(
    private val renderer: TextCompletionComboBoxRenderer<T>,
    private val editor: Editor<T>
  ) : ColoredListCellRenderer<T>() {
    override fun customizeCellRenderer(list: JList<out T>, value: T, index: Int, selected: Boolean, hasFocus: Boolean) {
      // Code completion prefix should be visible under cell selection
      mySelected = false

      myBorder = null

      val item = value ?: return
      renderer.customizeCellRenderer(this, item, editor.text)
    }
  }

  private class DefaultRenderer<T> : TextCompletionComboBoxRenderer<T> {
    override fun customizeCellRenderer(cell: SimpleColoredComponent, item: T, matchedText: @NlsSafe String) {
      cell.append(item?.toString() ?: "", REGULAR_ATTRIBUTES, matchedText, REGULAR_MATCHED_ATTRIBUTES)
    }
  }
}