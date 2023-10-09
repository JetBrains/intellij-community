// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.google.gson.JsonObject
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.move.MoveHandler
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.jsonUtils.getNullableString
import org.jetbrains.kotlin.idea.jsonUtils.getString
import org.jetbrains.kotlin.idea.refactoring.AbstractMultifileRefactoringTest
import org.jetbrains.kotlin.idea.refactoring.runRefactoringTest
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractK2MoveTest : AbstractMultifileRefactoringTest() {
    /**
     * Move tests are not 100% stable ATM, so we only run the tests that will definitely pass.
     *
     * Use this flag locally to find out which tests might be enabled.
     */
    private val onlyRunEnabledTests: Boolean = true

    override fun getProjectDescriptor() = ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()

    override fun isFirPlugin(): Boolean = true

    override fun isEnabled(config: JsonObject): Boolean = config.get("enabledInK2")?.asBoolean == true && onlyRunEnabledTests

    override fun runRefactoring(path: String, config: JsonObject, rootDir: VirtualFile, project: Project) {
        runMoveRefactoring(path, config, rootDir, project)
    }
}

fun runMoveRefactoring(path: String, config: JsonObject, rootDir: VirtualFile, project: Project) {
    runRefactoringTest(path, config, rootDir, project, K2MoveAction)
}

object K2MoveAction : AbstractMultifileRefactoringTest.RefactoringAction {
    override fun runRefactoring(rootDir: VirtualFile, mainFile: PsiFile, elementsAtCaret: List<PsiElement>, config: JsonObject) {
        val project = mainFile.project
        val source = if (mainFile is KtFile) buildSource(mainFile, config) else null
        val target = buildTarget(project, rootDir, config)
        if (source is K2MoveSource.FileSource && target is K2MoveTarget.SourceDirectory) {
            K2MoveFilesOrDirectoriesRefactoringProcessor(K2MoveDescriptor.Files(source, target)).run()
        } else if (source is K2MoveSource.ElementSource && target is K2MoveTarget.File) {
            K2MoveMembersRefactoringProcessor(K2MoveDescriptor.Members(source, target)).run()
        } else if (target is PsiElement) {
            MoveHandler.doMove(
                project,
                source?.elements?.toTypedArray() ?: arrayOf(mainFile),
                target,
                /* dataContext = */ null,
                /* callback = */ null
            )
        }
    }

    private fun buildSource(mainFile: KtFile, config: JsonObject): K2MoveSource<*> {
        val type = config.getString("type")
        return when (type) {
            "MOVE_FILES" -> K2MoveSource.FileSource(mainFile)
            "MOVE_KOTLIN_TOP_LEVEL_DECLARATIONS" -> K2MoveSource.FileSource(mainFile)
            else -> error("Unsupported type")
        }
    }

    private fun buildTarget(project: Project, rootDir: VirtualFile, config: JsonObject): Any {
        val targetPackage = config.getNullableString("targetPackage")
        val targetDir = config.getNullableString("targetDirectory") ?: targetPackage?.replace('.', '/')
        val targetFile = config.getNullableString("targetFile")
        return when {
            targetFile != null && targetDir != null -> error("Target can't both be file and directory")
            targetFile != null -> {
                val file = PsiManager.getInstance(project).findFile(rootDir.findFileByRelativePath(targetFile)!!)!!
                if (file is KtFile) K2MoveTarget.File(file) else targetFile
            }
            targetDir != null -> {
                runWriteAction { VfsUtil.createDirectoryIfMissing(rootDir, targetDir) }
                if (targetPackage != null) {
                    val pkg = JavaPsiFacade.getInstance(project).findPackage(targetPackage)!!
                    val directory = JavaPsiFacade.getInstance(project).findPackage(targetPackage)!!.directories.first()
                    K2MoveTarget.SourceDirectory(pkg, directory)
                } else {
                    val directory = rootDir.findFileByRelativePath(targetDir)!!.toPsiDirectory(project)!!
                    K2MoveTarget.SourceDirectory(directory)
                }
            }
            else -> error("No target specified")
        }
    }
}