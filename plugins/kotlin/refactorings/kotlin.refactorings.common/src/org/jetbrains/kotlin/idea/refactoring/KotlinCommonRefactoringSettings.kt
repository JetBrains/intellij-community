// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring

import com.intellij.openapi.components.service
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

/**
 * A common settings class to delegate to either K1 or K2 refactoring settings
 * from the common code.
 */
@Suppress("PropertyName")
interface KotlinCommonRefactoringSettings {
    var RENAME_SEARCH_FOR_TEXT_FOR_CLASS: Boolean
    var RENAME_SEARCH_FOR_TEXT_FOR_PROPERTY: Boolean
    var RENAME_SEARCH_FOR_TEXT_FOR_FUNCTION: Boolean
    var RENAME_SEARCH_IN_COMMENTS_FOR_CLASS: Boolean
    var RENAME_SEARCH_IN_COMMENTS_FOR_PROPERTY: Boolean
    var RENAME_SEARCH_IN_COMMENTS_FOR_FUNCTION: Boolean
    var RENAME_SEARCH_IN_COMMENTS_FOR_PARAMETER: Boolean
    var MOVE_PREVIEW_USAGES: Boolean
    var MOVE_SEARCH_IN_COMMENTS: Boolean
    var MOVE_SEARCH_FOR_TEXT: Boolean
    var MOVE_SEARCH_REFERENCES: Boolean
    var MOVE_MPP_DECLARATIONS: Boolean
    var INTRODUCE_DECLARE_WITH_VAR: Boolean
    var INTRODUCE_SPECIFY_TYPE_EXPLICITLY: Boolean

    /**
     * Indicates whether automatic file rename based on changed type name is suggested
     */
    var renameFileNames: Boolean

    /**
     * Indicates whether automatic variable rename based on changed type name is suggested
     */
    var renameVariables: Boolean

    /**
     * Indicates whether automatic parameter rename based on changed parameter name in super function is suggested
     */
    var renameParameterInHierarchy: Boolean

    /**
     * Indicates whether automatic inheritors rename based on changed super class name is suggested
     */
    var renameInheritors: Boolean

    /**
     * Indicates whether automatic functions rename based on changed overloaded function name is suggested
     */
    var renameOverloads: Boolean

    /**
     * Indicates whether automatic test methods rename based on changed production methods name is suggested
     */
    var renameTestMethods: Boolean

    var PULL_UP_MEMBERS_JAVADOC: Int

    var PUSH_DOWN_PREVIEW_USAGES: Boolean

    var EXTRACT_INTERFACE_JAVADOC: Int
    var EXTRACT_SUPERCLASS_JAVADOC: Int

    var INLINE_LOCAL_THIS: Boolean
    var INLINE_PROPERTY_KEEP: Boolean
    var INLINE_METHOD_THIS: Boolean
    var INLINE_METHOD_KEEP: Boolean
    var INLINE_TYPE_ALIAS_THIS: Boolean
    var INLINE_TYPE_ALIAS_KEEP: Boolean

    companion object {
        @JvmStatic
        fun getInstance(): KotlinCommonRefactoringSettings = service()
    }
}

/**
 * A convenience base class to implement [KotlinCommonRefactoringSettings] by delegating
 * to some concrete properties of type [T].
 *
 * @param T A type of property to delegate to.
 */
abstract class KotlinCommonRefactoringSettingsBase<T> : KotlinCommonRefactoringSettings {

    /**
     * Used to obtain the original instance of the properties. Should not cache anything.
     */
    protected abstract val instance: T

    /**
     * Use this function to perform the delegation to the original property of settings [T].
     *
     * Example of usage:
     *
     * ```kt
     * var COMMON_SETTING by delegateTo { it::SPECIFIC_SETTING }
     * ```
     *
     * N.B. This function intentionally does not accept [kotlin.reflect.KMutableProperty1],
     * because in such case we would have to write `delegateTo(SpecificConfig::SPECIFIC_SETTING)`.
     * We take a lambda producing [KMutableProperty0] instead, which allows for shorter and less verbose
     * `delegateTo { it::SPECIFIC_SETTING }` syntax.
     */
    protected fun <K> delegateTo(
        getOriginal: (T) -> KMutableProperty0<K>
    ): ReadWriteProperty<KotlinCommonRefactoringSettingsBase<T>, K> =
        object : ReadWriteProperty<KotlinCommonRefactoringSettingsBase<T>, K> {
            override fun getValue(thisRef: KotlinCommonRefactoringSettingsBase<T>, property: KProperty<*>): K {
                return getOriginal(thisRef.instance).get()
            }

            override fun setValue(thisRef: KotlinCommonRefactoringSettingsBase<T>, property: KProperty<*>, value: K) {
                getOriginal(thisRef.instance).set(value)
            }
        }
}