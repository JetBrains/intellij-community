// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.scratch

import com.intellij.ide.scratch.ScratchFileCreationHelper
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.statistics.KotlinCreateFileFUSCollector
import org.jetbrains.kotlin.parsing.KotlinParserDefinition

class KtScratchFileCreationHelper : ScratchFileCreationHelper() {

    override fun prepareText(project: Project, context: Context, dataContext: DataContext): Boolean {
        KotlinCreateFileFUSCollector.logFileTemplate("Kotlin Scratch")
        context.fileExtension = KotlinParserDefinition.STD_SCRIPT_SUFFIX

        return super.prepareText(project, context, dataContext)
    }

    override fun newScratchAllowed(): Boolean =
        if (KotlinPluginModeProvider.isK2Mode()) {
            false
        } else {
            super.newScratchAllowed()
        }
}