package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.SearchTextField
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IVoidSignal
import java.awt.Dimension
import java.awt.event.KeyEvent

class PackagesSmartSearchField(
    searchFieldFocus: IVoidSignal,
    lifetime: Lifetime
) : SearchTextField(false) {

    init {
        @Suppress("MagicNumber") // Swing dimension constants
        PackageSearchUI.setHeight(this, height = 25.scaled())

        @Suppress("MagicNumber") // Swing dimension constants
        minimumSize = Dimension(100.scaled(), minimumSize.height)

        textEditor.setTextToTriggerEmptyTextStatus(PackageSearchBundle.message("packagesearch.search.hint"))
        textEditor.emptyText.isShowAboveCenter = true

        PackageSearchUI.overrideKeyStroke(textEditor, "shift ENTER", this::transferFocusBackward)

        searchFieldFocus.advise(lifetime) { ApplicationManager.getApplication().invokeLater { requestFocus() } }
    }

    /**
     * Trying to navigate to the first element in the brief list
     * @return true in case of success; false if the list is empty
     */
    var goToTable: () -> Boolean = { false }

    override fun preprocessEventForTextField(e: KeyEvent?): Boolean {
        if (e?.keyCode == KeyEvent.VK_DOWN || e?.keyCode == KeyEvent.VK_PAGE_DOWN) {
            goToTable() // trying to navigate to the list instead of "show history"
            e.consume() // suppress default "show history" logic anyway
            return true
        }
        return super.preprocessEventForTextField(e)
    }

    override fun getBackground() = PackageSearchUI.HeaderBackgroundColor

    override fun onFocusLost() {
        super.onFocusLost()
        addCurrentTextToHistory()
    }
}
