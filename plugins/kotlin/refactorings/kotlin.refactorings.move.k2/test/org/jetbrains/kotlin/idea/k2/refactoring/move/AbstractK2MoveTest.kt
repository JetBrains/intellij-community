// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.google.gson.JsonObject
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.MoveHandler
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import com.intellij.refactoring.move.moveClassesOrPackages.MultipleRootsMoveDestination
import com.intellij.refactoring.move.moveInner.MoveInnerProcessor
import com.intellij.refactoring.move.moveMembers.MockMoveMembersOptions
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.jsonUtils.getNullableString
import org.jetbrains.kotlin.idea.jsonUtils.getString
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveFilesOrDirectoriesRefactoringProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveMembersRefactoringProcessor
import org.jetbrains.kotlin.idea.refactoring.AbstractMultifileRefactoringTest
import org.jetbrains.kotlin.idea.refactoring.runRefactoringTest
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

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
        if (doJavaMove(mainFile, elementsAtCaret, config)) return
        doKotlinMove(rootDir, mainFile, config)
    }

    private fun doJavaMove(mainFile: PsiFile, elementsAtCaret: List<PsiElement>, config: JsonObject): Boolean {
        val type = config.getString("type")
        when (type) {
          "MOVE_MEMBERS" -> {
              val members = elementsAtCaret.map { it.getNonStrictParentOfType<PsiMember>()!! }
              val targetClassName = config.getString("targetClass")
              val visibility = config.getNullableString("visibility")

              val options = MockMoveMembersOptions(targetClassName, members.toTypedArray())
              if (visibility != null) {
                  options.memberVisibility = visibility
              }
              MoveMembersProcessor(mainFile.project, options).run()
              return true
          }
          "MOVE_INNER_CLASS" -> {
              val project = mainFile.project

              val classToMove = elementsAtCaret.single().getNonStrictParentOfType<PsiClass>()!!
              val newClassName = config.getNullableString("newClassName") ?: classToMove.name!!
              val outerInstanceParameterName = config.getNullableString("outerInstanceParameterName")
              val targetPackage = config.getString("targetPackage")

              MoveInnerProcessor(
                  project,
                  classToMove,
                  newClassName,
                  outerInstanceParameterName != null,
                  outerInstanceParameterName,
                  JavaPsiFacade.getInstance(project).findPackage(targetPackage)!!.directories[0]
              ).run()
              return true
          }
          "MOVE_TOP_LEVEL_CLASSES" -> {
              val classesToMove = elementsAtCaret.map { it.getNonStrictParentOfType<PsiClass>()!! }
              val targetPackage = config.getString("targetPackage")
              MoveClassesOrPackagesProcessor(
                  mainFile.project,
                  classesToMove.toTypedArray(),
                  MultipleRootsMoveDestination(PackageWrapper(mainFile.manager, targetPackage)),
                  /* searchInComments = */ false,
                  /* searchInNonJavaFiles = */ true,
                  /* moveCallback = */ null
              ).run()
              return true
          }
          else -> return false
        }
    }

    private fun doKotlinMove(rootDir: VirtualFile, mainFile: PsiFile, config: JsonObject) {
        fun getFilesToMove(project: Project, rootDir: VirtualFile, config: JsonObject): Set<PsiFileSystemItem>? {
            return config.getAsJsonArray("filesToMove")?.map { filePath ->
                val virtualFile = rootDir.findFileByRelativePath(filePath.asString) ?: error("Can't find file ${filePath.asString}")
                if (virtualFile.isDirectory) virtualFile.toPsiDirectory(project)!! else virtualFile.toPsiFile(project)!!
            }?.toSet()
        }

        fun buildSource(files: Set<KtFile>, config: JsonObject): K2MoveSourceDescriptor<*> {
            val type = config.getString("type")
            return when (type) {
                "MOVE_FILES", "MOVE_KOTLIN_TOP_LEVEL_DECLARATIONS", "MOVE_FILES_WITH_DECLARATIONS" -> K2MoveSourceDescriptor.FileSource(files)
                else -> error("Unsupported test type")
            }
        }

        fun buildTarget(project: Project, rootDir: VirtualFile, config: JsonObject): Any {
            val targetPackage = config.getNullableString("targetPackage")
            val targetDir = config.getNullableString("targetDirectory") ?: targetPackage?.replace('.', '/')
            val targetFile = config.getNullableString("targetFile")
            return when {
                targetFile != null && targetDir != null -> error("Target can't both be file and directory")
                targetFile != null -> {
                    val file = PsiManager.getInstance(project).findFile(rootDir.findFileByRelativePath(targetFile)!!)!!
                    if (file is KtFile) K2MoveTargetDescriptor.File(file) else targetFile
                }
                targetDir != null -> {
                    runWriteAction { VfsUtil.createDirectoryIfMissing(rootDir, targetDir) }
                    if (targetPackage != null) {
                        val pkg = JavaPsiFacade.getInstance(project).findPackage(targetPackage)!!
                        val directory = JavaPsiFacade.getInstance(project).findPackage(targetPackage)!!.directories.first()
                        K2MoveTargetDescriptor.SourceDirectory(pkg, directory)
                    } else {
                        val directory = rootDir.findFileByRelativePath(targetDir)!!.toPsiDirectory(project)!!
                        K2MoveTargetDescriptor.SourceDirectory(directory)
                    }
                }
                else -> error("No target specified")
            }
        }

        val project = mainFile.project
        val filesToMove = getFilesToMove(project, rootDir, config) ?: setOf(mainFile)
        val source = if (filesToMove.all { it is KtFile }) {
            @Suppress("UNCHECKED_CAST")
            buildSource(filesToMove as Set<KtFile>, config)
        } else null
        val target = buildTarget(project, rootDir, config)
        if (source is K2MoveSourceDescriptor.FileSource && target is K2MoveTargetDescriptor.SourceDirectory) {
            val descriptor = K2MoveDescriptor.Files(source, target, true, true, true)
            K2MoveFilesOrDirectoriesRefactoringProcessor(descriptor).run()
        } else if (source is K2MoveSourceDescriptor.ElementSource && target is K2MoveTargetDescriptor.File) {
            val descriptor = K2MoveDescriptor.Members(source, target, true, true, true)
            K2MoveMembersRefactoringProcessor(descriptor).run()
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
}