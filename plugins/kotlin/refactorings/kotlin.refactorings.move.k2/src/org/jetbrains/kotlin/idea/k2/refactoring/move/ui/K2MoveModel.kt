// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.bindSelected
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import kotlin.reflect.KMutableProperty0

/**
 * @see org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveDescriptor
 */
internal sealed class K2MoveModel {
    abstract val source: K2MoveSourceModel<*>

    abstract val target: K2MoveTargetModel

    val searchForText: Setting = Setting.SEARCH_FOR_TEXT

    val searchInComments: Setting = Setting.SEARCH_IN_COMMENTS

    val searchReferences: Setting = Setting.SEARCH_REFERENCES

    abstract fun toDescriptor(): K2MoveDescriptor

    internal enum class Setting(private val text: @NlsContexts.Checkbox String, val setting: KMutableProperty0<Boolean>) {
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
}