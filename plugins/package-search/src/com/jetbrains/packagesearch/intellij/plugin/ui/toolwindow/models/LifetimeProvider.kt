package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.openapi.Disposable
import com.jetbrains.rd.util.lifetime.Lifetime

internal interface LifetimeProvider {

    val lifetime: Lifetime
    val parentDisposable: Disposable
}
