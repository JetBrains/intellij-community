// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring

import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettingsBase

@Suppress("SuspiciousCallableReferenceInLambda")
internal class K2CommonRefactoringSettings : KotlinCommonRefactoringSettingsBase<KotlinFirRefactoringsSettings>() {
    override val instance: KotlinFirRefactoringsSettings
        get() = KotlinFirRefactoringsSettings.instance

    override var RENAME_SEARCH_IN_COMMENTS_FOR_CLASS: Boolean
            by delegateTo { it::RENAME_SEARCH_IN_COMMENTS_FOR_CLASS }

    override var RENAME_SEARCH_FOR_TEXT_FOR_CLASS: Boolean
            by delegateTo { it::RENAME_SEARCH_FOR_TEXT_FOR_CLASS }

    override var RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION: Boolean
            by delegateTo { it::RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION }

    override var RENAME_SEARCH_FOR_TEXT_FOR_FUNCTION: Boolean
            by delegateTo { it::RENAME_SEARCH_FOR_TEXT_FOR_FUNCTION }

    override var RENAME_SEARCH_IN_COMMENTS_FOR_PARAMETER: Boolean
            by delegateTo { it::RENAME_SEARCH_IN_COMMENTS_FOR_PARAMETER }

    override var RENAME_SEARCH_IN_COMMENTS_FOR_PROPERTY: Boolean
            by delegateTo { it::RENAME_SEARCH_IN_COMMENTS_FOR_PROPERTY }

    override var RENAME_SEARCH_FOR_TEXT_FOR_PROPERTY: Boolean
            by delegateTo { it::RENAME_SEARCH_IN_COMMENTS_FOR_PROPERTY }

    override var MOVE_PREVIEW_USAGES: Boolean
            by delegateTo { it::MOVE_PREVIEW_USAGES }

    override var MOVE_SEARCH_IN_COMMENTS: Boolean
            by delegateTo { it::MOVE_SEARCH_IN_COMMENTS }

    override var MOVE_SEARCH_FOR_TEXT: Boolean
            by delegateTo { it::MOVE_SEARCH_FOR_TEXT }

    override var MOVE_DELETE_EMPTY_SOURCE_FILES: Boolean
            by delegateTo { it::MOVE_DELETE_EMPTY_SOURCE_FILES }

    override var MOVE_MPP_DECLARATIONS: Boolean
            by delegateTo { it::MOVE_MPP_DECLARATIONS }
}
