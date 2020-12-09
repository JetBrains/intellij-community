package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.completion

import com.intellij.ide.plugins.newui.SearchPopupCallback
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SearchTextField
import com.jetbrains.packagesearch.intellij.plugin.api.query.SearchQueryCompletionModel
import com.jetbrains.packagesearch.intellij.plugin.api.query.SearchQueryCompletionProvider
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JList
import kotlin.math.min

class SearchCompletionPopupHandler(
    val searchField: SearchTextField,
    private val completionProvider: SearchQueryCompletionProvider
) {

    private var popup: SearchCompletionPopup? = null
    private val popupListener: JBPopupListener = object : JBPopupListener {
        override fun onClosed(event: LightweightWindowEvent) {
            popup = null
        }
    }

    init {
        searchField.textEditor.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.isConsumed || preprocessEventForTextField(e)) e.consume()
            }

            override fun keyReleased(e: KeyEvent) {
                if (e.isActionKey) return

                handleCompletion()
            }
        })
    }

    fun preprocessEventForTextField(e: KeyEvent?): Boolean {
        if (popup?.itemsList == null) return false

        if (e?.keyCode == KeyEvent.VK_ENTER ||
            e?.keyChar == '\n') {

            popup?.itemsList?.let {
                if (it.selectedIndex != -1) {
                    it.dispatchEvent(e)
                    return true
                }
            }
        }

        if (e?.keyCode == KeyEvent.VK_DOWN || e?.keyCode == KeyEvent.VK_UP) {
            popup?.itemsList?.let {
                if (e.keyCode == KeyEvent.VK_DOWN && it.selectedIndex == -1) {
                    it.selectedIndex = 0
                } else {
                    it.dispatchEvent(e)
                }

                return true
            }
        }

        return false
    }

    fun handleCompletion() {
        val searchQuery = searchField.text
        val caretPosition = searchField.textEditor.caretPosition

        val completionModel = completionProvider.buildCompletionModel(searchQuery, caretPosition)
        if (completionModel == null) {
            hidePopup()
        } else {
            showPopup(completionModel)
        }
    }

    fun hidePopup() {
        popup?.hide()
        popup = null
    }

    private fun showPopup(completionModel: SearchQueryCompletionModel) {
        hidePopup()

        // Sanity check: ensure we have something to display
        if (completionModel.attributes.isNullOrEmpty() &&
            completionModel.values.isNullOrEmpty()) return

        // Behaviour check: when only one completion item is ready AND we already typed it,
        // there is no need to display a completion popup
        if (!completionModel.prefix.isNullOrEmpty()) {
            if (completionModel.attributes?.count() == 1 && completionModel.attributes.first() == completionModel.prefix) return
            if (completionModel.values?.count() == 1 && completionModel.values.first() == completionModel.prefix) return
        }

        // Start building popup
        val callback = object : SearchPopupCallback(completionModel.prefix) {
            override fun consume(value: String) {
                searchField.text = searchField.text.replaceRange(
                    completionModel.caretPosition,
                    completionModel.endPosition,
                    value
                )

                searchField.textEditor.caretPosition = min(
                    searchField.text.length,
                    completionModel.caretPosition + value.length
                )
            }
        }

        val renderer = object : ColoredListCellRenderer<String>() {
          override fun customizeCellRenderer(
            list: JList<out String>,
            @NlsSafe value: String?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
          ) {
            append(value as String)
          }
        }

        popup = SearchCompletionPopup(
            searchField,
            completionModel,
            popupListener
        )
            .apply {
                createAndShow(callback, renderer, true)
            }
    }
}
