// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move

import com.intellij.openapi.util.NlsContexts
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import javax.swing.JComponent
import kotlin.reflect.KMutableProperty0

sealed class K2MoveDescriptor {
    abstract val source: K2MoveSource<*>

    abstract val target: K2MoveTarget

    abstract val refactoringProcessor: BaseRefactoringProcessor

    protected abstract val settings: Array<Array<Setting>>

    protected enum class Setting(private val text: @NlsContexts.Checkbox String, val setting: KMutableProperty0<Boolean>) {
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
        ),

        MOVE_DELETE_EMPTY_SOURCE_FILES(
            KotlinBundle.message("checkbox.text.delete.empty.source.files"),
            KotlinCommonRefactoringSettings.getInstance()::MOVE_DELETE_EMPTY_SOURCE_FILES
        );

        context(Panel)
        fun createComboBox() {
            row {
                checkBox(text).bindSelected(setting)
            }
        }
    }

    context(Panel)
    fun createPanel(onError: (String?, JComponent) -> Unit) {
        target.buildPanel(onError)
        source.buildPanel(onError)
        row {
            panel {
                settings.firstOrNull()?.forEach { settings -> settings.createComboBox() }
            }.align(AlignX.LEFT).align(AlignY.TOP)
            panel {
                settings.lastOrNull()?.forEach { settings -> settings.createComboBox() }
            }.align(AlignX.RIGHT).align(AlignY.TOP)
        }
    }

    /**
     * A file preserved move is when a user moves 1 or more files into a directory. All the files will be preserved here, they will just
     * be moved into a different location.
     */
    class Files(override val source: K2MoveSource.FileSource, override val target: K2MoveTarget.SourceDirectory) : K2MoveDescriptor() {
        override val refactoringProcessor: BaseRefactoringProcessor get() = K2MoveFilesRefactoringProcessor(this)

        val searchForText: Boolean get() = KotlinCommonRefactoringSettings.getInstance().MOVE_SEARCH_FOR_TEXT

        val searchInComments: Boolean get() = KotlinCommonRefactoringSettings.getInstance().MOVE_SEARCH_IN_COMMENTS

        val searchReferences: Boolean get() = KotlinCommonRefactoringSettings.getInstance().MOVE_SEARCH_REFERENCES

        override val settings: Array<Array<Setting>> = arrayOf(
            arrayOf(Setting.SEARCH_FOR_TEXT, Setting.SEARCH_IN_COMMENTS),
            arrayOf(Setting.SEARCH_REFERENCES)
        )
    }

    /**
     * A file member moves is when the user moves the members inside the files, not considering the files themselves.
     */
    class Members(override val source: K2MoveSource.ElementSource, override val target: K2MoveTarget.File) : K2MoveDescriptor() {
        override val refactoringProcessor: BaseRefactoringProcessor get() = K2MoveMembersRefactoringProcessor(this)

        val searchForText: Boolean get() = KotlinCommonRefactoringSettings.getInstance().MOVE_SEARCH_FOR_TEXT

        val searchInComments: Boolean get() = KotlinCommonRefactoringSettings.getInstance().MOVE_SEARCH_IN_COMMENTS

        val searchReferences: Boolean get() = KotlinCommonRefactoringSettings.getInstance().MOVE_SEARCH_REFERENCES

        val deleteEmptySourceFiles: Boolean get() = KotlinCommonRefactoringSettings.getInstance().MOVE_SEARCH_REFERENCES

        override val settings: Array<Array<Setting>> = arrayOf(
            arrayOf(Setting.SEARCH_FOR_TEXT, Setting.SEARCH_IN_COMMENTS),
            arrayOf(Setting.SEARCH_REFERENCES, Setting.MOVE_DELETE_EMPTY_SOURCE_FILES)
        )
    }
}