// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performance.performancePlugin.commands

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vfs.findDirectory
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.impl.file.PsiDirectoryImpl
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import com.intellij.performanceTesting.vcs.VcsTestUtil
import com.intellij.psi.impl.PsiManagerEx
import io.opentelemetry.context.Context
import org.jetbrains.kotlin.idea.actions.createKotlinFileFromTemplate

/**
 * Command to add Kotlin file to project
 * Example: %createKotlinFile fileName, dstDir, fileType - data, file, enum, interface, sealed, annotation, script, worksheet, object]
 */
internal class CreateKotlinFileCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

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
        private val LOG = Logger.getInstance(CreateKotlinFileCommand::class.java)
    }

    override suspend fun doExecute(context: PlaybackContext) {
        val (fileName, filePath, fileType) = extractCommandArgument(PREFIX).replace("\\s","").split(",")
        val directory = PsiDirectoryImpl(
            PsiManagerEx.getInstanceEx(context.project),
            (context.project.guessProjectDir() ?: throw RuntimeException("Root of the project was not found "))
                .findDirectory(filePath) ?: throw RuntimeException("Can't find file $filePath")
        )

        val templateName = POSSIBLE_FILE_TYPES[fileType.lowercase()]
        if (templateName == null) throw RuntimeException("File type must be one of '${POSSIBLE_FILE_TYPES.keys}'")
        val template = FileTemplateManager.getInstance(directory.project).getInternalTemplate(templateName)

        //Disable vcs dialog which appears on adding new file to the project tree
        VcsTestUtil.provisionVcsAddFileConfirmation(context.project, VcsTestUtil.VcsAddFileConfirmation.DO_NOTHING)

        ApplicationManager.getApplication().invokeAndWait(Context.current().wrap(Runnable {
            PerformanceTestSpan.TRACER.spanBuilder(NAME).use {
                val createdFile = createKotlinFileFromTemplate(fileName, template, directory)
                createdFile?.let {
                    LOG.info("Created kotlin file\n${createdFile.text}")
                }
            }
        }))

    }

    override fun getName(): String = NAME

}