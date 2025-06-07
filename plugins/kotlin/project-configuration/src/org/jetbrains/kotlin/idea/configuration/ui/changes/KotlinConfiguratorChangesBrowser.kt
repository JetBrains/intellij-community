// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration.ui.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListChange
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.util.containers.ContainerUtil

internal class KotlinConfiguratorChangesBrowser(project: Project, private val changes: List<Change>, private val changeSelector: (Change) -> Unit) :
    AsyncChangesBrowserBase(project, false, false), Disposable {

    override val changesTreeModel: AsyncChangesTreeModel = SimpleAsyncChangesTreeModel.create { grouping ->
        TreeModelBuilder.buildFromChanges(myProject, grouping, changes, null)
    }

    private fun selectFirstChangeAfterRefresh() {
        viewer.invokeAfterRefresh {
            val selection = VcsTreeModelData.getListSelectionOrAll(viewer).map { it as? Change }
            selection.list.firstOrNull()?.let(::selectChange)
        }
    }

    override fun createPopupMenuActions(): MutableList<AnAction> {
        val actions = mutableListOf<AnAction>()
        ContainerUtil.addIfNotNull(actions, ActionManager.getInstance().getAction("Diff.ShowStandaloneDiff"))
        return actions
    }

    private fun callSelectionCallback() {
        val selection = VcsTreeModelData.getListSelectionOrAll(viewer).map { it as? Change }
        val selectedChange = selection.list.getOrNull(selection.selectedIndex) ?: return
        changeSelector(selectedChange)
    }

    private fun selectChange(change: Change) {
        val virtualFile = change.virtualFile ?: return
        viewer.selectFile(virtualFile)
    }

    init {
        init()
        viewer.setInclusionModel(DefaultInclusionModel(ChangeListChange.HASHING_STRATEGY))
        viewer.rebuildTree()

        viewer.setDoubleClickHandler {
            // Only change diff viewer by selection
            true
        }

        viewer.setEnterKeyHandler {
            // Only change diff viewer by selection
            false
        }

        viewer.addTreeSelectionListener {
            callSelectionCallback()
        }

        selectFirstChangeAfterRefresh()
    }

    override fun dispose() {
        shutdown()
    }
}