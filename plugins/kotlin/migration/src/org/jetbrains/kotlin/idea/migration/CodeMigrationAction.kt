// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.migration

import com.intellij.openapi.actionSystem.AnActionEvent

class CodeMigrationAction : CodeInspectionAction(
    KotlinMigrationBundle.message("inspection.migration.title"),
    KotlinMigrationBundle.message("inspection.migration.title")
) {
    override fun update(e: AnActionEvent) {
        super.update(e)

        val project = e.project
        if (project != null) {
            e.presentation.isEnabledAndVisible = CodeMigrationToggleAction.isEnabled(project)
        }
    }

    override fun getHelpTopic(): String {
        return "reference.dialogs.cleanup.scope"
    }

    companion object {
        const val ACTION_ID = "KotlinCodeMigration"
    }
}
