// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.openapi.observable.properties.ObservableProperty
import org.jetbrains.annotations.ApiStatus

/**
 * Top-level interface for combined UI settings of the move refactoring.
 *
 * The purpose of the interface is to share state between different parts of the UI model via observable properties.
 * This is necessary for tracking component changes between different parts of the UI model when presentation should be conditional.
 *
 * All sub-interface implementors must be registered before access to their properties.
 * Normally a shared settings instance is passed to the model part constructor and used for registration.
 */
@ApiStatus.Internal
interface ObservableUiSettings : K2MoveModelObservableSettings, K2SourceModelObservableSettings, K2TargetModelObservableSettings {
    fun registerK2MoveModelSettings(settings: K2MoveModelObservableSettings)
    fun registerK2SourceModelSettings(settings: K2SourceModelObservableSettings)
    fun registerK2TargetModelSettings(settings: K2TargetModelObservableSettings)
}

/**
 * Move refactoring settings providing properties of the [K2MoveModel] useful for other parts of the UI model.
 */
@ApiStatus.Internal
interface K2MoveModelObservableSettings {
    /**
     * State of the 'Search references' setting.
     */
    val searchReferencesSettingObservable: ObservableProperty<Boolean>

    /**
     * State of the 'Search for text occurrences' setting.
     */
    val searchForTextSettingObservable: ObservableProperty<Boolean>

    /**
     * State of the 'Search in comments and strings' setting.
     */
    val searchInCommentsSettingObservable: ObservableProperty<Boolean>

    /**
     * State of the 'Move expect/actual counterparts' setting.
     */
    val mppDeclarationsSettingObservable: ObservableProperty<Boolean>
}

/**
 * Move refactoring settings providing properties of the [K2MoveSourceModel] useful for other parts of the UI model.
 */
@ApiStatus.Internal
interface K2SourceModelObservableSettings {
    /**
     * Shows whether among the selected elements about to be moved there are 'expect' or 'actual' declarations.
     */
    val mppDeclarationsSelectedObservable: ObservableProperty<Boolean>
}

/**
 * Move refactoring settings providing properties of the [K2MoveTargetModel] useful for other parts of the UI model.
 */
@ApiStatus.Internal
interface K2TargetModelObservableSettings {
    /**
     * Provides information about detected platform suffix in the currently selected target file.
     * Platform suffixes are part of the naming convention in multiplatform projects.
     * [kotlinlang documentation](https://kotlinlang.org/docs/coding-conventions.html#multiplatform-projects)
     *
     * The value is `null` for non-multiplatform projects
     *
     * The value is `null` if the selected file does not have a suffix matching the platform part of the source set name
     *
     * Example: the suffix of a file with the name `myFile.jvm.kt` is `jvm` in `jvm`/`jvmMain`/`jvmTest` source sets; `null` otherwise.
     */
    val sourceSetSuffix: ObservableProperty<String?>
}

internal class ObservableUiSettingsImpl : ObservableUiSettings {
    private lateinit var k2MoveModelSettings: K2MoveModelObservableSettings
    private lateinit var k2TargetModelSettings: K2TargetModelObservableSettings
    private lateinit var k2SourceModelSettings: K2SourceModelObservableSettings

    override val searchReferencesSettingObservable: ObservableProperty<Boolean>
        get() = k2MoveModelSettings.searchReferencesSettingObservable
    override val searchForTextSettingObservable: ObservableProperty<Boolean>
        get() = k2MoveModelSettings.searchForTextSettingObservable
    override val searchInCommentsSettingObservable: ObservableProperty<Boolean>
        get() = k2MoveModelSettings.searchInCommentsSettingObservable
    override val mppDeclarationsSettingObservable: ObservableProperty<Boolean>
        get() = k2MoveModelSettings.mppDeclarationsSettingObservable

    override val mppDeclarationsSelectedObservable: ObservableProperty<Boolean>
        get() = k2SourceModelSettings.mppDeclarationsSelectedObservable

    override val sourceSetSuffix: ObservableProperty<String?>
        get() = k2TargetModelSettings.sourceSetSuffix

    override fun registerK2MoveModelSettings(settings: K2MoveModelObservableSettings) {
        k2MoveModelSettings = settings
    }

    override fun registerK2SourceModelSettings(settings: K2SourceModelObservableSettings) {
        k2SourceModelSettings = settings
    }

    override fun registerK2TargetModelSettings(settings: K2TargetModelObservableSettings) {
        k2TargetModelSettings = settings
    }
}
