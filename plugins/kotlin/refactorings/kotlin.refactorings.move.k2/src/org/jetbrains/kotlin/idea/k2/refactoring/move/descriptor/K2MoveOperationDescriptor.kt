// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.move.MoveCallback
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveDeclarationsRefactoringProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2MoveFilesOrDirectoriesRefactoringProcessor
import org.jetbrains.kotlin.idea.search.ExpectActualUtils
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeAsciiOnly

sealed class K2MoveOperationDescriptor<T : K2MoveDescriptor>(
    val project: Project,
    val moveDescriptors: List<T>,
    val searchForText: Boolean,
    val searchInComments: Boolean,
    val searchReferences: Boolean,
    val dirStructureMatchesPkg: Boolean,
    val moveCallBack: MoveCallback? = null
) {
    init {
        require(moveDescriptors.isNotEmpty()) { "No move descriptors were provided" }
    }

    abstract val sourceElements: List<PsiElement>

    abstract fun refactoringProcessor(): BaseRefactoringProcessor

    class Files(
        project: Project,
        moveDescriptors: List<K2MoveDescriptor.Files>,
        searchForText: Boolean,
        searchInComments: Boolean,
        searchReferences: Boolean,
        dirStructureMatchesPkg: Boolean,
        moveCallBack: MoveCallback? = null
    ) : K2MoveOperationDescriptor<K2MoveDescriptor.Files>(
        project,
        moveDescriptors,
        searchForText,
        searchInComments,
        searchReferences,
        dirStructureMatchesPkg,
        moveCallBack
    ) {
        override val sourceElements: List<PsiFileSystemItem> get() = moveDescriptors.flatMap { it.source.elements }

        override fun refactoringProcessor(): BaseRefactoringProcessor {
            return K2MoveFilesOrDirectoriesRefactoringProcessor(this)
        }
    }

    class Declarations(
        project: Project,
        moveDescriptors: List<K2MoveDescriptor.Declarations>,
        searchForText: Boolean,
        searchInComments: Boolean,
        searchReferences: Boolean,
        dirStructureMatchesPkg: Boolean,
        internal val outerInstanceParameterNameProvider: (declaration: KtNamedDeclaration) -> String? = DefaultOuterInstanceParameterNameProvider,
        moveCallBack: MoveCallback? = null,
        internal val preDeclarationMoved: (KtNamedDeclaration) -> Unit = { },
        internal val postDeclarationMoved: (KtNamedDeclaration, KtNamedDeclaration) -> Unit = { _, _ -> },
    ) : K2MoveOperationDescriptor<K2MoveDescriptor.Declarations>(
        project,
        moveDescriptors,
        searchForText,
        searchInComments,
        searchReferences,
        dirStructureMatchesPkg,
        moveCallBack
    ) {
        override val sourceElements: List<KtNamedDeclaration> get() = moveDescriptors.flatMap { it.source.elements }

        override fun refactoringProcessor(): BaseRefactoringProcessor {
            return K2MoveDeclarationsRefactoringProcessor(this)
        }
    }

    companion object {
        /**
         * This parameter name provider attempts to find a name for the outer instance
         * based on the outer classes name and avoids name clashes by attempting to
         * choose a name that is not already taken anywhere in the declaration.
         */
        private val DefaultOuterInstanceParameterNameProvider = (fun (declaration: KtNamedDeclaration): String? {
            val suggestedName = declaration.containingClass()?.takeIf {
                (declaration !is KtClass || declaration.isInner()) && declaration !is KtProperty
            }?.name?.decapitalizeAsciiOnly() ?: return null

            val usedNames = mutableSetOf<String>()
            declaration.accept(object : KtTreeVisitorVoid() {
                override fun visitNamedDeclaration(declaration: KtNamedDeclaration) {
                    super.visitNamedDeclaration(declaration)
                    usedNames.add(declaration.nameAsSafeName.asString())
                }

                override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                    super.visitSimpleNameExpression(expression)
                    expression.getIdentifier()?.text?.let { usedNames.add(it) }
                }
            })

            if (suggestedName !in usedNames) return suggestedName
            for (i in 1..1000) {
                val nameWithNum = "$suggestedName$i"
                if (nameWithNum !in usedNames) return nameWithNum
            }
            return null
        })

        @RequiresReadLock
        fun Declarations(
            project: Project,
            declarations: Collection<KtNamedDeclaration>,
            baseDir: PsiDirectory,
            fileName: String,
            pkgName: FqName,
            searchForText: Boolean,
            searchReferences: Boolean,
            searchInComments: Boolean,
            mppDeclarations: Boolean,
            dirStructureMatchesPkg: Boolean,
            moveCallBack: MoveCallback? = null
        ): Declarations {
            if (mppDeclarations && declarations.any { it.isExpectDeclaration() || it.hasActualModifier() }) {
                val descriptors = declarations.flatMap { elem ->
                    ExpectActualUtils.withExpectedActuals(elem).filterIsInstance<KtNamedDeclaration>()
                }.groupBy { elem ->
                    ProjectFileIndex.getInstance(project).getSourceRootForFile(elem.containingFile.virtualFile)?.toPsiDirectory(project)
                }.mapNotNull { (baseDir, elements) ->
                    if (baseDir == null) return@mapNotNull null
                    val srcDescriptor = K2MoveSourceDescriptor.ElementSource(elements)
                    val targetDescriptor = K2MoveTargetDescriptor.File(fileName, pkgName, baseDir)
                    K2MoveDescriptor.Declarations(project, srcDescriptor, targetDescriptor)
                }
                return Declarations(
                    project = project,
                    moveDescriptors = descriptors,
                    searchForText = searchForText,
                    searchInComments = searchReferences,
                    searchReferences = searchInComments,
                    dirStructureMatchesPkg = dirStructureMatchesPkg,
                    moveCallBack = moveCallBack
                )
            } else {
                val srcDescr = K2MoveSourceDescriptor.ElementSource(declarations)
                val targetDescr = K2MoveTargetDescriptor.File(fileName, pkgName, baseDir)
                val moveDescriptor = K2MoveDescriptor.Declarations(project, srcDescr, targetDescr)
                return Declarations(
                    project = project,
                    moveDescriptors = listOf(moveDescriptor),
                    searchForText = searchForText,
                    searchInComments = searchInComments,
                    searchReferences = searchReferences,
                    dirStructureMatchesPkg = dirStructureMatchesPkg,
                    moveCallBack = moveCallBack
                )
            }
        }
    }
}