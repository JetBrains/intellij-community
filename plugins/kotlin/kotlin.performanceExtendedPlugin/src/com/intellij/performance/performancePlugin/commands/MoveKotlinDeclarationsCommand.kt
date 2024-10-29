// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performance.performancePlugin.commands

import com.jetbrains.performancePlugin.commands.OpenFileCommand
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.psi.PsiManager
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts
import com.intellij.refactoring.move.MoveHandler
import com.jetbrains.performancePlugin.commands.dto.MoveDeclarationsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedDeclaration

/**
 * The command moves declarations from one specified source file to another.
 * Argument is serialized [MoveDeclarationsData] as json.
 * NB: Different MoveHandlerDelegates are used to move declarations!
 */
class MoveKotlinDeclarationsCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
    companion object {
        const val NAME = "moveDeclarations"
        const val PREFIX = "$CMD_PREFIX$NAME"
        val LOG = Logger.getInstance(MoveKotlinDeclarationsCommand::class.java)
    }

    private fun findFile(project: Project, path: String): VirtualFile {
        return OpenFileCommand.findFile(path, project) ?: throw IllegalArgumentException("File not found: $path")
    }

    override suspend fun doExecute(context: PlaybackContext) {
        val project = context.project
        val psiManager = PsiManager.getInstance(project)
        val moveDeclarationData = deserializeOptionsFromJson(extractCommandArgument(PREFIX), MoveDeclarationsData::class.java)
        val tag = if (moveDeclarationData.spanTag.isNotEmpty()) "_${moveDeclarationData.spanTag}" else ""
        withContext(Dispatchers.EDT) {
            // Currently Refactor / Move with K2 supports only top-level declarations.
            // We just select all top-level declarations whose names match any provided name.
            writeIntentReadAction {
                val file = findFile(project, moveDeclarationData.fromFile)
                val declarations = psiManager.findFile(file)!!.children.filter {
                    it is KtNamedDeclaration && moveDeclarationData.declarations.contains(it.name)
                }.toTypedArray()
                LOG.info("${declarations.joinToString()}.")
                val toFile = psiManager.findFile(findFile(project, moveDeclarationData.toFile))
                TelemetryManager.getTracer(Scope("MoveDeclarations")).spanBuilder("$NAME$tag").use {
                    withIgnoredConflicts<Throwable> {
                        MoveHandler.doMove(project, declarations, toFile, null, null)
                    }
                }
            }
        }
    }

    override fun getName(): String {
        return NAME
    }
}
