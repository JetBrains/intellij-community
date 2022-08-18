// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.kotlin.tools.projectWizard.wizard

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.JBProtocolCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.tools.projectWizard.projectTemplates.ProjectTemplate

internal class OpenNewProjectWizardProtocolCommand : JBProtocolCommand(COMMAND_NAME) {
    override suspend fun execute(target: String?, parameters: Map<String, String>, fragment: String?): String? {
        if (target != NEW_PROJECT_TARGET) {
            return IdeBundle.message("jb.protocol.unknown.target", target)
        } else {
            val template = parameters.get(NEW_PROJECT_TARGET_TEMPLATE_PARAMETER)?.let(ProjectTemplate.Companion::byId)
            withContext(Dispatchers.EDT) {
                NewWizardOpener.open(template)
            }
            return null
        }
    }

    companion object {
        private const val COMMAND_NAME = "kotlin-wizard"
        private const val NEW_PROJECT_TARGET = "create-project"
        private const val NEW_PROJECT_TARGET_TEMPLATE_PARAMETER = "template"
    }
}
