// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.openapi.observable.properties.ObservableProperty

interface ObservableUiSettings : K2MoveModelObservableSettings, K2SourceModelObservableSettings, K2TargetModelObservableSettings {
    fun registerK2MoveModelSettings(settings: K2MoveModelObservableSettings)
    fun registerK2SourceModelSettings(settings: K2SourceModelObservableSettings)
    fun registerK2TargetModelSettings(settings: K2TargetModelObservableSettings)
}

interface K2MoveModelObservableSettings {
    val searchReferencesSettingObservable: ObservableProperty<Boolean>
    val searchForTextSettingObservable: ObservableProperty<Boolean>
    val searchInCommentsSettingObservable: ObservableProperty<Boolean>
    val mppDeclarationsSettingObservable: ObservableProperty<Boolean>
}

interface K2SourceModelObservableSettings {
    val mppDeclarationsSelectedObservable: ObservableProperty<Boolean>
}

interface K2TargetModelObservableSettings {
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
