// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.bindSelected
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import kotlin.reflect.KMutableProperty0

/**
 * @see org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
 */
sealed class K2MoveModel {
    abstract val source: K2MoveSourceModel<*>

    abstract val target: K2MoveTargetModel

    val searchForText: Setting = Setting.SEARCH_FOR_TEXT

    val searchInComments: Setting = Setting.SEARCH_IN_COMMENTS

    val searchReferences: Setting = Setting.SEARCH_REFERENCES

    abstract fun toDescriptor(): K2MoveDescriptor

    enum class Setting(private val text: @NlsContexts.Checkbox String, val setting: KMutableProperty0<Boolean>) {
        SEARCH_FOR_TEXT(
            KotlinBundle.message("search.for.text.occurrences"),
            KotlinCommonRefactoringSettings.getInstance()::MOVE_SEARCH_FOR_TEXT
        ),

        SEARCH_IN_COMMENTS(
            KotlinBundle.message("search.in.comments.and.strings"),
            KotlinCommonRefactoringSettings.getInstance()::MOVE_SEARCH_IN_COMMENTS
        ),

        SEARCH_REFERENCES(
            KotlinBundle.message("checkbox.text.search.references"),
            KotlinCommonRefactoringSettings.getInstance()::MOVE_SEARCH_REFERENCES
        );

        var state: Boolean = setting.get()
            private set

        context(Panel)
        fun createComboBox() {
            row {
                checkBox(text).bindSelected(::state)
            }.layout(RowLayout.PARENT_GRID)
        }
    }

    /**
     * @see org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor.Files
     */
    class Files(override val source: K2MoveSourceModel.FileSource, override val target: K2MoveTargetModel.SourceDirectory) : K2MoveModel() {
        override fun toDescriptor(): K2MoveDescriptor {
            val srcDescr = source.toDescriptor()
            val targetDescr = target.toDescriptor()
            return K2MoveDescriptor.Files(srcDescr, targetDescr, searchForText.state, searchReferences.state, searchInComments.state)
        }
    }

    /**
     * @see org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor.Members
     */
    class Members(override val source: K2MoveSourceModel.ElementSource, override val target: K2MoveTargetModel.File) : K2MoveModel() {
        override fun toDescriptor(): K2MoveDescriptor {
            val srcDescr = source.toDescriptor()
            val targetDescr = target.toDescriptor()
            return K2MoveDescriptor.Members(srcDescr, targetDescr, searchForText.state, searchReferences.state, searchInComments.state)
        }
    }

    companion object {
        fun create(elements: Array<out PsiElement>, targetContainer: PsiElement?): K2MoveModel {
            /** When moving elements to or from a class we expect the user to want to move them to the containing file instead */
            fun KtElement.correctForProjectView(): KtElement {
                val containingFile = containingKtFile
                if (containingFile.declarations.singleOrNull() == this) return containingFile
                return this
            }

            val correctedTarget = if (targetContainer is KtElement) targetContainer.correctForProjectView() else targetContainer
            val elementsToMove = elements.map { (it as? KtElement)?.correctForProjectView() }.toSet()
            return when {
                elementsToMove.all { it is KtFile } -> {
                    val source = K2MoveSourceModel.FileSource(elementsToMove.filterIsInstance<KtFile>().toSet())
                    val target = if (correctedTarget is PsiDirectory) {
                        K2MoveTargetModel.SourceDirectory(correctedTarget)
                    } else { // no default target is provided, happens when invoking refactoring via keyboard instead of drag-and-drop
                        val file = elementsToMove.firstOrNull()?.containingKtFile ?: error("No default target found")
                        K2MoveTargetModel.SourceDirectory(file.containingDirectory ?: error("No default target found"))
                    }
                    Files(source, target)
                }

                elementsToMove.all { it is KtNamedDeclaration } -> {
                    val source = K2MoveSourceModel.ElementSource(elementsToMove.filterIsInstance<KtNamedDeclaration>().toSet())
                    val target = if (correctedTarget is KtFile) {
                        K2MoveTargetModel.File(correctedTarget)
                    } else { // no default target is provided, happens when invoking refactoring via keyboard instead of drag-and-drop
                        val file = elementsToMove.firstOrNull()?.containingKtFile ?: error("No default target found")
                        K2MoveTargetModel.File(file)
                    }
                    Members(source, target)
                }
                else -> error("Can't mix file and element source")
            }
        }
    }
}