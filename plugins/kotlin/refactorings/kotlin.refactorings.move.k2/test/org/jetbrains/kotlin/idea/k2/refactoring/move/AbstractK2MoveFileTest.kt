// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.google.gson.JsonObject
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.psi.JavaDirectoryService
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
import org.jetbrains.kotlin.idea.refactoring.AbstractMultifileRefactoringTest
import org.jetbrains.kotlin.idea.refactoring.runRefactoringTest
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.junit.jupiter.api.fail

abstract class AbstractK2MoveFileTest : AbstractMultifileRefactoringTest() {
    override fun getProjectDescriptor() = ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()

    override fun isFirPlugin(): Boolean = true

    override fun isEnabled(config: JsonObject): Boolean = config.get("enabledInK2")?.asBoolean == true

    override fun runRefactoring(path: String, config: JsonObject, rootDir: VirtualFile, project: Project) {
        runMoveRefactoring(path, config, rootDir, project)
    }

    override fun fileFilter(file: VirtualFile): Boolean {
        if (file.isFile && file.extension == "kt") {
            if (file.name.endsWith(".k2.kt")) return true
            val k2CounterPart = file.parent.findChild("${file.nameWithoutExtension}.k2.kt")
            if (k2CounterPart?.isFile == true) return false
        }
        return super.fileFilter(file)
    }

    override fun fileNameMapper(file: VirtualFile): String =
        file.name.replace(".k2.kt", ".kt")
}

private fun runMoveRefactoring(path: String, config: JsonObject, rootDir: VirtualFile, project: Project) {
    runRefactoringTest(path, config, rootDir, project, K2MoveFileRefactoringAction)
}

private object K2MoveFileRefactoringAction : KotlinMoveRefactoringAction {
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
                /* searchInComments = */ config.searchInComments(),
                /* searchInNonJavaFiles = */ true,
                /* moveCallback = */ null,
                /* prepareSuccessfulCallback = */ null
            ).run()
        } else {
            val fileNames = config.getAsJsonArray("filesToMove")?.map { it.asString }
                ?: listOfNotNull(config.getString("mainFile"))
            if (fileNames.isEmpty()) fail("No file name specified")
            val files = fileNames.map { path -> rootDir.findFileByRelativePath(path)?.toPsiFile(project) as KtFile }.toSet()
            val sourceDescriptor = K2MoveSourceDescriptor.FileSource(files)
            val targetPackage = config.getNullableString("targetPackage")
            val targetDir = config.getNullableString("targetDirectory")
            val targetDescriptor = when {
                targetDir != null -> {
                    val directory =  runWriteAction { VfsUtil.createDirectoryIfMissing(rootDir, targetDir) }.toPsiDirectory(project)!!
                    if (targetPackage != null) {
                        K2MoveTargetDescriptor.SourceDirectory(FqName(targetPackage), directory)
                    } else {
                        val pkg = JavaDirectoryService.getInstance().getPackage(directory) ?: error("No package was found")
                        K2MoveTargetDescriptor.SourceDirectory(FqName(pkg.qualifiedName), directory)
                    }
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
                config.searchReferences(), config.searchInComments(), config.searchReferences()
            )
            K2MoveFilesOrDirectoriesRefactoringProcessor(descriptor).run()
        }
    }
}