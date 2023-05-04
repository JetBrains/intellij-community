// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performance.performancePlugin.commands

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vfs.findFileOrDirectory
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.impl.file.PsiDirectoryImpl
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter

class CreateKotlinFileCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

    companion object {
        const val NAME = "createKotlinFile"
        const val PREFIX = CMD_PREFIX + NAME
        val POSSIBLE_FILE_TYPES = mapOf(
            Pair("class", "Kotlin Class"),
            Pair("script", "Kotlin script"),
            Pair("worksheet", "Kotlin worksheet"),
            Pair("data", "Kotlin Data Class"),
            Pair("enum", "Kotlin Enum"),
            Pair("annotation", "Kotlin Annotation"),
            Pair("object", "Kotlin Object"),
            Pair("file", "Kotlin File"),
            Pair("interface", "Kotlin Interface")
        )
    }

    override suspend fun doExecute(context: PlaybackContext) {
        val (fileName, filePath, fileType) = extractCommandArgument(PREFIX).split(",")
        val directory = PsiDirectoryImpl(
            PsiManagerImpl(context.project),
            context.project.guessProjectDir()!!.findFileOrDirectory(filePath)!!
        )

        val templateName = POSSIBLE_FILE_TYPES.getOrElse(fileType.lowercase()) { return@getOrElse null }
        if (templateName == null) throw RuntimeException("File type must be one of ${POSSIBLE_FILE_TYPES.keys}")
        val template = FileTemplateManager.getInstance(directory.project).getInternalTemplate(templateName)

        ApplicationManager.getApplication().invokeAndWait {
            CreateFileFromTemplateAction.createFileFromTemplate(fileName, template, directory, null, true)
        }
    }

    override fun getName(): String {
        return NAME
    }

}