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
    runRefactoringTest(path, config, rootDir, project, K2MoveAction.valueOf(config.getString("type")))
}

enum class K2MoveAction : AbstractMultifileRefactoringTest.RefactoringAction {
    MOVE_FILES {
        override fun runRefactoring(rootDir: VirtualFile, mainFile: PsiFile, elementsAtCaret: List<PsiElement>, config: JsonObject) {
            val project = mainFile.project
            val targetPackage = config.getNullableString("targetPackage")
            val targetDirPath = targetPackage?.replace('.', '/') ?: config.getNullableString("targetDirectory")
            if (targetDirPath != null) {
                runWriteAction { VfsUtil.createDirectoryIfMissing(rootDir, targetDirPath) }
                val source = K2MoveSource.FileSource(mainFile as KtFile)
                val target = if (targetPackage != null) {
                    val pkg = JavaPsiFacade.getInstance(project).findPackage(targetPackage)!!
                    val directory = JavaPsiFacade.getInstance(project).findPackage(targetPackage)!!.directories[0]
                    K2MoveTarget.SourceDirectory(pkg, directory)
                } else {
                    val directory = rootDir.findFileByRelativePath(targetDirPath)!!.toPsiDirectory(project)!!
                    K2MoveTarget.SourceDirectory(directory)
                }
                K2MoveFilesOrDirectoriesRefactoringProcessor(K2MoveDescriptor.Files(source, target)).run()
            } else {
                val targetFile = config.getString("targetFile")
                MoveHandler.doMove(
                    project,
                    arrayOf(mainFile),
                    PsiManager.getInstance(project).findFile(rootDir.findFileByRelativePath(targetFile)!!)!!,
                    /* dataContext = */ null,
                    /* callback = */ null
                )
            }
        }
    }
}