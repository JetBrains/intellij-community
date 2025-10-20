// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.openapi.observable.properties.ObservableBooleanProperty

interface ObservableUiSettings : K2MoveModelObservableSettings {
    fun registerK2MoveModelSettings(settings: K2MoveModelObservableSettings)
}

interface K2MoveModelObservableSettings {
    val searchReferencesObservable: ObservableBooleanProperty
    val searchForTextObservable: ObservableBooleanProperty
    val searchInCommentsObservable: ObservableBooleanProperty
    val mppDeclarationsObservable: ObservableBooleanProperty
}

internal class ObservableUiSettingsImpl : ObservableUiSettings {
    private lateinit var k2MoveModelSettings: K2MoveModelObservableSettings

    override val searchReferencesObservable: ObservableBooleanProperty
        get() = k2MoveModelSettings.searchReferencesObservable
    override val searchForTextObservable: ObservableBooleanProperty
        get() = k2MoveModelSettings.searchForTextObservable
    override val searchInCommentsObservable: ObservableBooleanProperty
        get() = k2MoveModelSettings.searchInCommentsObservable
    override val mppDeclarationsObservable: ObservableBooleanProperty
        get() = k2MoveModelSettings.mppDeclarationsObservable

    override fun registerK2MoveModelSettings(settings: K2MoveModelObservableSettings) {
        k2MoveModelSettings = settings
    }
}
