// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.google.gson.JsonObject
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
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
import org.jetbrains.kotlin.idea.base.util.getString
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveSourceDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveMembersRefactoringProcessor
import org.jetbrains.kotlin.idea.refactoring.AbstractMultifileRefactoringTest
import org.jetbrains.kotlin.idea.refactoring.runRefactoringTest
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

abstract class AbstractK2MoveTest : AbstractMultifileRefactoringTest() {
    /**
     * Can be enabled to find tests tha are passing but are still disabled.
     */
    private val onlyPassingDisabledTests: Boolean = false

    override fun getProjectDescriptor() = ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()

    override fun isFirPlugin(): Boolean = true

    override fun isEnabled(config: JsonObject): Boolean = config.get("enabledInK2")?.asBoolean == true || onlyPassingDisabledTests

    override fun runRefactoring(path: String, config: JsonObject, rootDir: VirtualFile, project: Project) {
        if (config.get("enabledInK2")?.asBoolean == true && onlyPassingDisabledTests) fail()
        Registry.get("kotlin.k2.smart.move").setValue(true, testRootDisposable)
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

fun runMoveRefactoring(path: String, config: JsonObject, rootDir: VirtualFile, project: Project) {
    runRefactoringTest(path, config, rootDir, project, K2MoveAction)
}

object K2MoveAction : AbstractMultifileRefactoringTest.RefactoringAction {

    override fun runRefactoring(rootDir: VirtualFile, mainFile: PsiFile, elementsAtCaret: List<PsiElement>, config: JsonObject) {
        if (doJavaMove(mainFile, elementsAtCaret, config)) return
        doKotlinMove(rootDir, elementsAtCaret, mainFile, config)
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

    private fun doKotlinMove(rootDir: VirtualFile, elementsAtCaret: List<PsiElement>, mainFile: PsiFile, config: JsonObject) {
        fun getFilesToMove(project: Project, rootDir: VirtualFile, config: JsonObject): Set<PsiFileSystemItem>? {
            return config.getAsJsonArray("filesToMove")?.map { filePath ->
                val virtualFile = rootDir.findFileByRelativePath(filePath.asString) ?: error("Can't find file ${filePath.asString}")
                if (virtualFile.isDirectory) virtualFile.toPsiDirectory(project)!! else virtualFile.toPsiFile(project)!!
            }?.toSet()
        }

        fun buildSource(files: Set<KtFile>, elementsAtCaret: List<PsiElement>, config: JsonObject): K2MoveSourceDescriptor<*> {
            val type = config.getString("type")
            return when (type) {
                "MOVE_FILES", "MOVE_FILES_WITH_DECLARATIONS" -> K2MoveSourceDescriptor.FileSource(files)
                "MOVE_KOTLIN_TOP_LEVEL_DECLARATIONS" -> {
                    if (elementsAtCaret.isNotEmpty()) {
                        K2MoveSourceDescriptor.ElementSource(elementsAtCaret.filterIsInstance<KtNamedDeclaration>().toSet())
                    } else {
                        K2MoveSourceDescriptor.FileSource(files)
                    }
                }
                else -> error("Unsupported test type")
            }
        }

        fun buildTarget(project: Project, rootDir: VirtualFile, config: JsonObject): Any {
            val targetPackage = config.getNullableString("targetPackage")
            val targetDir = config.getNullableString("targetDirectory")
            val targetFile = config.getNullableString("targetFile")
            return when {
                targetFile != null && targetDir != null -> error("Target can't both be file and directory")
                targetFile != null -> {
                    val file = PsiManager.getInstance(project).findFile(rootDir.findFileByRelativePath(targetFile)!!)!!
                    if (file is KtFile) K2MoveTargetDescriptor.File(file) else targetFile
                }
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
                else -> error("No target specified")
            }
        }

        val project = mainFile.project
        val filesToMove = getFilesToMove(project, rootDir, config) ?: setOf(mainFile)
        val source = if (filesToMove.all { it is KtFile }) {
            @Suppress("UNCHECKED_CAST")
            buildSource(filesToMove as Set<KtFile>, elementsAtCaret, config)
        } else null
        val specifiedTarget = buildTarget(project, rootDir, config)
        if (source is K2MoveSourceDescriptor.FileSource && specifiedTarget is K2MoveTargetDescriptor.SourceDirectory) {
            val descriptor = K2MoveDescriptor.Files(project, source, specifiedTarget, true, true, true)
            descriptor.refactoringProcessor().run()
        } else if (source is K2MoveSourceDescriptor.ElementSource) {
            val actualTarget = when (specifiedTarget) {
                is K2MoveTargetDescriptor.File -> specifiedTarget
                is K2MoveTargetDescriptor.SourceDirectory -> {
                    K2MoveTargetDescriptor.File(
                        source.elements.first().name?.capitalizeAsciiOnly() + ".kt",
                        specifiedTarget.pkgName,
                        specifiedTarget.baseDirectory
                    )
                }
                else -> throw IllegalStateException("Invalid specified target")
            }
            val descriptor = K2MoveDescriptor.Members(project, source, actualTarget, true, true, true)
            K2MoveMembersRefactoringProcessor(descriptor).run()
        } else if (specifiedTarget is PsiElement) {
            MoveHandler.doMove(
                project,
                source?.elements?.toTypedArray() ?: arrayOf(mainFile),
                specifiedTarget,
                /* dataContext = */ null,
                /* callback = */ null
            )
        }
    }
}