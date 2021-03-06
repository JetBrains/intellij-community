package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.completion

import com.intellij.ide.plugins.newui.SearchPopupCallback
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.util.Consumer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.packagesearch.intellij.plugin.api.query.SearchQueryCompletionModel
import java.awt.Component
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JList
import javax.swing.SwingUtilities
import javax.swing.event.CaretEvent
import javax.swing.event.CaretListener
import javax.swing.text.BadLocationException

class SearchCompletionPopup(
    private val searchField: SearchTextField,
    private val completionModel: SearchQueryCompletionModel,
    private val popupListener: JBPopupListener
) : ComponentAdapter(), CaretListener {

    private var skipCaretEvent: Boolean = false

    private var searchPopupCallback: SearchPopupCallback? = null
    private var jbPopup: JBPopup? = null
    private var windowEvent: LightweightWindowEvent? = null
    private var dialogComponent: Component? = null
    var itemsList: JList<String>? = null

    @Suppress("DEPRECATION")
    fun createAndShow(callback: Consumer<in String>, renderer: ColoredListCellRenderer<in String>, async: Boolean) {
        if (callback is SearchPopupCallback) {
            searchPopupCallback = callback
        }

        val ipad = renderer.ipad
        ipad.right = getXOffset()
        ipad.left = ipad.right
        renderer.font = searchField.textEditor.font

        val completionData = mutableListOf<String>()
        if (!completionModel.values.isNullOrEmpty()) {
            completionData.addAll(completionModel.values)
        } else if (!completionModel.attributes.isNullOrEmpty()) {
            completionData.addAll(completionModel.attributes)
        }

        val list = JBList(completionData)
        val popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
            .setMovable(false)
            .setResizable(false)
            .setRequestFocus(false)
            .setItemChosenCallback(callback)
            .setFont(searchField.textEditor.font)
            .setRenderer(renderer)
            .createPopup()

        itemsList = list
        jbPopup = popup
        windowEvent = LightweightWindowEvent(popup)

        skipCaretEvent = true

        popup.addListener(popupListener)
        searchField.textEditor.addCaretListener(this)

        dialogComponent = searchField.textEditor.rootPane.parent
        dialogComponent?.addComponentListener(this)

        if (async) {
            SwingUtilities.invokeLater { this.show() }
        } else {
            show()
        }
    }

    private fun getXOffset(): Int {
        val i = if (UIUtil.isUnderWin10LookAndFeel()) 5 else UIUtil.getListCellHPadding()
        return JBUI.scale(i)
    }

    private fun getPopupLocation(): Point {
        val location = try {
            val view = searchField.textEditor.modelToView(completionModel.caretPosition)
            Point(view.maxX.toInt(), view.maxY.toInt())
        } catch (ignore: BadLocationException) {
            searchField.textEditor.caret.magicCaretPosition
        }

        SwingUtilities.convertPointToScreen(location, searchField.textEditor)
        location.x -= getXOffset() + JBUI.scale(2)
        location.y += 2

        return location
    }

    private fun isValid(): Boolean {
        val popup = jbPopup
        return popup != null && popup.isVisible && popup.content.parent != null
    }

    private fun update() {
        skipCaretEvent = true

        jbPopup?.setLocation(getPopupLocation())
        jbPopup?.pack(true, true)
    }

    private fun show() {
        if (jbPopup != null) {
            itemsList?.clearSelection()
            jbPopup?.showInScreenCoordinates(searchField.textEditor, getPopupLocation())
        }
    }

    fun hide() {
        searchField.textEditor.removeCaretListener(this)

        dialogComponent?.removeComponentListener(this)
        dialogComponent = null

        jbPopup?.cancel()
        jbPopup = null
    }

    override fun caretUpdate(e: CaretEvent) {
        if (skipCaretEvent) {
            skipCaretEvent = false
        } else {
            hide()
            popupListener.onClosed(windowEvent!!)
        }
    }

    override fun componentMoved(e: ComponentEvent?) {
        if (jbPopup != null && isValid()) {
            update()
        }
    }

    override fun componentResized(e: ComponentEvent?) {
        componentMoved(e)
    }
}
