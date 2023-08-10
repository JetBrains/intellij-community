// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * @see org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
 */
@State(name = "KotlinFirRefactoringSettings", storages = [Storage("kotlinFirRefactoring.xml")], category = SettingsCategory.CODE)
class KotlinFirRefactoringsSettings : PersistentStateComponent<KotlinFirRefactoringsSettings> {
    var RENAME_SEARCH_FOR_TEXT_FOR_VARIABLE: Boolean = false
    var RENAME_SEARCH_FOR_TEXT_FOR_FUNCTION: Boolean = false
    var RENAME_SEARCH_FOR_TEXT_FOR_CLASS: Boolean = false
    var RENAME_SEARCH_IN_COMMENTS_FOR_PROPERTY: Boolean = false
    var RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION: Boolean = false
    var RENAME_SEARCH_IN_COMMENTS_FOR_CLASS: Boolean = false
    var RENAME_SEARCH_IN_COMMENTS_FOR_PARAMETER: Boolean = false

    var renameVariables: Boolean = true
    var renameParameterInHierarchy: Boolean = true
    var renameInheritors: Boolean = true
    var renameOverloads: Boolean = true

    var MOVE_PREVIEW_USAGES: Boolean = true
    var MOVE_SEARCH_IN_COMMENTS: Boolean = true
    var MOVE_SEARCH_FOR_TEXT: Boolean = true
    var MOVE_SEARCH_REFERENCES: Boolean = true
    var MOVE_MPP_DECLARATIONS: Boolean = true
    var INTRODUCE_DECLARE_WITH_VAR: Boolean = false
    var INTRODUCE_SPECIFY_TYPE_EXPLICITLY: Boolean = false

    override fun getState() = this

    override fun loadState(state: KotlinFirRefactoringsSettings) = XmlSerializerUtil.copyBean(state, this)

    companion object {
        @JvmStatic
        val instance: KotlinFirRefactoringsSettings
            get() = service()
    }
}