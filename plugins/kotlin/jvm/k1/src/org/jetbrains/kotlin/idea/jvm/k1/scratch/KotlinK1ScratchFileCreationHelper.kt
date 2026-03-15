// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k1.scratch

import com.intellij.ide.scratch.ScratchFileCreationHelper
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.statistics.KotlinCreateFileFUSCollector
import org.jetbrains.kotlin.parsing.KotlinParserDefinition

class KotlinK1ScratchFileCreationHelper : ScratchFileCreationHelper() {

    override fun prepareText(project: Project, context: Context, dataContext: DataContext): Boolean {
        KotlinCreateFileFUSCollector.logFileTemplate("Kotlin Scratch")
        context.fileExtension = KotlinParserDefinition.STD_SCRIPT_SUFFIX

        return true
    }

    override fun beforeCreate(
        project: Project,
        context: Context
    ) {
        KotlinCreateFileFUSCollector.logFileTemplate("Kotlin Scratch From Selection")
        context.fileExtension = KotlinParserDefinition.STD_SCRIPT_SUFFIX
    }
}