// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.common

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@Volatile
private var freeModeForTests: Boolean? = null

@ApiStatus.Internal
fun isPyCharmFreeMode(): Boolean =
  freeModeForTests ?: (PlatformUtils.isPyCharm() && PluginManagerCore.isDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID))

/** Makes [isPyCharmFreeMode] give [freeMode] until [disposable] is disposed. */
@TestOnly
@ApiStatus.Internal
fun setPyCharmFreeModeForTests(freeMode: Boolean, disposable: Disposable) {
  freeModeForTests = freeMode
  Disposer.register(disposable) { freeModeForTests = null }
}