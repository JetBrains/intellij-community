// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.ObservableBooleanProperty
import com.intellij.openapi.observable.properties.ObservableProperty

open class ConstantObservableProperty<T>(private val value: T) : ObservableProperty<T> {
    final override fun get(): T = value
    final override fun afterChange(listener: (T) -> Unit) {}
    final override fun afterChange(parentDisposable: Disposable?, listener: (T) -> Unit) {}
}

class ConstantBooleanObservableProperty(value: Boolean) : ConstantObservableProperty<Boolean>(value), ObservableBooleanProperty {
    override fun afterSet(parentDisposable: Disposable?, listener: () -> Unit) {}
    override fun afterReset(parentDisposable: Disposable?, listener: () -> Unit) {}
}
