// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.jsonUtils.getNullableString
import org.jetbrains.kotlin.idea.jsonUtils.getString
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveFilesOrDirectoriesRefactoringProcessor
import org.jetbrains.kotlin.idea.refactoring.runRefactoringTest
import org.jetbrains.kotlin.name.FqName
import org.junit.jupiter.api.fail

abstract class AbstractK2MoveFileOrDirectoriesTest : AbstractMultifileMoveRefactoringTest() {
    override fun runRefactoring(path: String, config: JsonObject, rootDir: VirtualFile, project: Project) {
        runRefactoringTest(path, config, rootDir, project, K2MoveFileOrDirectoriesRefactoringAction)
    }
}

internal object K2MoveFileOrDirectoriesRefactoringAction : KotlinMoveRefactoringAction {
    override fun runRefactoring(rootDir: VirtualFile, mainFile: PsiFile, elementsAtCaret: List<PsiElement>, config: JsonObject) {
        val project = mainFile.project
        if (mainFile.name.endsWith(".java")) {
            val targetPackage = config.getNullableString("targetPackage")
            val targetDirPath = targetPackage?.replace('.', '/') ?: config.getNullableString("targetDirectory") ?: return
            val newParent = if (targetPackage != null) {
                JavaPsiFacade.getInstance(project).findPackage(targetPackage)!!.directories[0]
            } else {
                rootDir.findFileByRelativePath(targetDirPath)!!.toPsiDirectory(project)!!
            }
            MoveFilesOrDirectoriesProcessor(
                project,
                arrayOf(mainFile),
                newParent,
                config.searchInComments(),
                /* searchInNonJavaFiles = */ true,
                /* moveCallback = */ null,
                /* prepareSuccessfulCallback = */ null
            ).run()
        } else {
            val fileNames = config.getAsJsonArray("filesToMove")?.map { it.asString }
                ?: listOfNotNull(config.getString("mainFile"))
            if (fileNames.isEmpty()) fail("No file name specified")
            val files = fileNames.mapNotNull { path ->
                val vFile = rootDir.findFileByRelativePath(path)
                vFile?.toPsiFile(project) ?: vFile?.toPsiDirectory(project)
            }.toSet()
            val sourceDescriptor = K2MoveSourceDescriptor.FileSource(files)
            val targetPackage = config.getNullableString("targetPackage")
            val targetDir = config.getNullableString("targetDirectory")
            val targetDescriptor = when {
                targetDir != null -> {
                    val targetDirectory = rootDir.findFileByRelativePath(targetDir)?.toPsiDirectory(project)!!
                    K2MoveTargetDescriptor.SourceDirectory(targetDirectory)
                }
                targetPackage != null -> {
                    K2MoveTargetDescriptor.SourceDirectory(FqName(targetPackage), rootDir.toPsiDirectory(project)!!)
                }

                else -> fail("No target specified")
            }
            val descriptor = K2MoveDescriptor.Files(
              project,
              sourceDescriptor,
              targetDescriptor,
              shouldUpdateReferences(config, sourceDescriptor.elements.first(), targetDescriptor.baseDirectory),
              config.searchInComments(),
              config.searchReferences(),
              config.moveExpectedActuals()
            )
            K2MoveFilesOrDirectoriesRefactoringProcessor(descriptor).run()
        }
    }
}