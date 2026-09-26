// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.MutableBooleanProperty
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings

/**
 * Subset of refactoring settings used in K2 move refactoring.
 */
@ApiStatus.Internal
enum class MoveModelSetting(internal val text: @NlsContexts.Checkbox String) {
    SEARCH_FOR_TEXT(KotlinBundle.message("search.for.text.occurrences")) {
        override var state: Boolean
            get() {
                return KotlinCommonRefactoringSettings.getInstance().MOVE_SEARCH_FOR_TEXT
            }
            set(value) {
                KotlinCommonRefactoringSettings.getInstance().MOVE_SEARCH_FOR_TEXT = value
            }
    },

    SEARCH_IN_COMMENTS(KotlinBundle.message("search.in.comments.and.strings")) {
        override var state: Boolean
            get() {
                return KotlinCommonRefactoringSettings.getInstance().MOVE_SEARCH_IN_COMMENTS
            }
            set(value) {
                KotlinCommonRefactoringSettings.getInstance().MOVE_SEARCH_IN_COMMENTS = value
            }
    },


    SEARCH_REFERENCES(KotlinBundle.message("checkbox.text.search.references")) {
        override var state: Boolean
            get() {
                return KotlinCommonRefactoringSettings.getInstance().MOVE_SEARCH_REFERENCES
            }
            set(value) {
                KotlinCommonRefactoringSettings.getInstance().MOVE_SEARCH_REFERENCES = value
            }
    },

    MPP_DECLARATIONS(KotlinBundle.message("label.text.move.expect.actual.counterparts")) {
        override var state: Boolean
            get() {
                return KotlinCommonRefactoringSettings.getInstance().MOVE_MPP_DECLARATIONS
            }
            set(value) {
                KotlinCommonRefactoringSettings.getInstance().MOVE_MPP_DECLARATIONS = value
            }
    };

    abstract var state: Boolean
}

/**
 * A wrapper for [MoveModelSetting] for holding the observable property.
 *
 * Holding the observable property in the [MoveModelSetting] enum directly can lead to Project leaks through the property's listeners.
 * Instances of this class should not be referenced from places that can outlast the Project, e.g., statically.
 */
@ApiStatus.Internal
class BoundMoveModelSetting(private val setting: MoveModelSetting) {
    var state: Boolean
        get() = setting.state
        set(value) {
            setting.state = value
        }

    val text: String
        get() = setting.text

    // lazy to avoid service access from constructor
    internal val observableProperty: MutableBooleanProperty by lazy { AtomicBooleanProperty(setting.state) }
}
