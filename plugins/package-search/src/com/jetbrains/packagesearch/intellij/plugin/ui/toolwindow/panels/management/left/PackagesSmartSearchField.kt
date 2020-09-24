package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.left

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.JBUI
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.api.query.SearchQueryCompletionProvider
import com.jetbrains.packagesearch.intellij.plugin.ui.RiderUI
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.completion.SearchCompletionPopupHandler
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchToolWindowModel
import java.awt.Dimension
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.KeyEvent

class PackagesSmartSearchField(
    val viewModel: PackageSearchToolWindowModel,
    completionProvider: SearchQueryCompletionProvider
) : SearchTextField(false) {

    private val completionPopupHandler = SearchCompletionPopupHandler(this, completionProvider)

    init {
        RiderUI.setHeight(this, height = 25)

        @Suppress("MagicNumber") // Gotta love Swing APIs
        minimumSize = Dimension(JBUI.scale(100), minimumSize.height)

        font = RiderUI.BigFont
        textEditor.setTextToTriggerEmptyTextStatus(PackageSearchBundle.message("packagesearch.search.hint"))
        textEditor.emptyText.isShowAboveCenter = true

        RiderUI.overrideKeyStroke(textEditor, "shift ENTER", this::transferFocusBackward)

        viewModel.focusSearchBox.advise(viewModel.lifetime) { ApplicationManager.getApplication().invokeLater { requestFocus() } }

        textEditor.addFocusListener(object : FocusListener {
            override fun focusLost(e: FocusEvent?) {
                completionPopupHandler.hidePopup()
            }

            override fun focusGained(e: FocusEvent?) {
                // No-op
            }
        })
    }

    /**
     * Trying to navigate to the first element in the brief list
     * @return true in case of success; false if the list is empty
     */
    var goToList: () -> Boolean = { false }

    override fun preprocessEventForTextField(e: KeyEvent?): Boolean {
        // If completion popup is showing, we want to ignore some events (key down/up)
        if (completionPopupHandler.preprocessEventForTextField(e)) {
            e?.consume()
            return true
        }

        // If not, we can run our own logic
        if (e?.keyCode == KeyEvent.VK_DOWN || e?.keyCode == KeyEvent.VK_PAGE_DOWN) {
            goToList() // trying to navigate to the list instead of "show history"
            e.consume() // suppress default "show history" logic anyway
            return true
        }
        return super.preprocessEventForTextField(e)
    }

    override fun getBackground() = RiderUI.HeaderBackgroundColor
}
