// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import org.jetbrains.kotlin.idea.base.util.KOTLIN_FILE_EXTENSIONS

@Service
class ToggleKotlinVariablesState {
    companion object {
        private const val KOTLIN_VARIABLE_VIEW = "debugger.kotlin.variable.view"

        fun getService(): ToggleKotlinVariablesState = service()
    }

    var kotlinVariableView = PropertiesComponent.getInstance().getBoolean(KOTLIN_VARIABLE_VIEW, true)
        set(newValue) {
            field = newValue
            PropertiesComponent.getInstance().setValue(KOTLIN_VARIABLE_VIEW, newValue)
        }
}

class ToggleKotlinVariablesView : ToggleAction() {
    private val kotlinVariableViewService = ToggleKotlinVariablesState.getService()

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        val session = e.getData(XDebugSession.DATA_KEY)
        e.presentation.isEnabledAndVisible = session != null && session.isInKotlinFile()
    }

    private fun XDebugSession.isInKotlinFile(): Boolean {
        val fileExtension = currentPosition?.file?.extension ?: return false
        return fileExtension in KOTLIN_FILE_EXTENSIONS
    }

    override fun isSelected(e: AnActionEvent) = kotlinVariableViewService.kotlinVariableView

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        kotlinVariableViewService.kotlinVariableView = state
        XDebuggerUtilImpl.rebuildAllSessionsViews(e.project)
    }
}