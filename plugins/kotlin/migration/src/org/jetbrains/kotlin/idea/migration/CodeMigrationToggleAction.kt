// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.migration

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.Project

class CodeMigrationToggleAction : ToggleAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        return Manager.isEnabled(project)
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        Manager.setEnabled(project, state)
    }

    object Manager {
        private const val MIGRATION_OPTION = "kotlin.migration.detection.enabled"

        fun setEnabled(project: Project, state: Boolean) {
            PropertiesComponent.getInstance(project).setValue(MIGRATION_OPTION, state, true)
        }

        fun isEnabled(project: Project): Boolean {
            return PropertiesComponent.getInstance(project).getBoolean(MIGRATION_OPTION, true)
        }
    }
}

