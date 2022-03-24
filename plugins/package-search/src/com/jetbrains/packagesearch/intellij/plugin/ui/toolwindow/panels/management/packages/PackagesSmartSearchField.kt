package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.panels.management.packages

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.ui.SearchTextField
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.Subscription
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.util.scaled
import com.jetbrains.packagesearch.intellij.plugin.util.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.awt.event.KeyEvent

class PackagesSmartSearchField(
    searchFieldFocus: Flow<Unit>,
    project: Project
) : SearchTextField(false) {

    init {
        @Suppress("MagicNumber") // Swing dimension constants
        PackageSearchUI.setHeight(this, height = 25)

        @Suppress("MagicNumber") // Swing dimension constants
        minimumSize = Dimension(100.scaled(), minimumSize.height)

        textEditor.setTextToTriggerEmptyTextStatus(PackageSearchBundle.message("packagesearch.search.hint"))
        textEditor.emptyText.isShowAboveCenter = true

        PackageSearchUI.overrideKeyStroke(textEditor, "shift ENTER", this::transferFocusBackward)

        searchFieldFocus
            .onEach { withContext(Dispatchers.EDT) { requestFocus() } }
            .launchIn(project.lifecycleScope)
    }

    /**
     * Trying to navigate to the first element in the brief list
     * @return true in case of success; false if the list is empty
     */
    var goToTable: () -> Boolean = { false }

    var fieldClearedListener: (() -> Unit)? = null

    private val listeners = mutableSetOf<(KeyEvent) -> Unit>()

    fun registerOnKeyPressedListener(action: (KeyEvent) -> Unit): Subscription {
        listeners.add(action)
        return Subscription { listeners.remove(action) }
    }

    override fun preprocessEventForTextField(e: KeyEvent?): Boolean {
        e?.let { keyEvent -> listeners.forEach { listener -> listener(keyEvent) } }
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

    override fun onFieldCleared() {
        super.onFieldCleared()
        fieldClearedListener?.invoke()
    }
}
