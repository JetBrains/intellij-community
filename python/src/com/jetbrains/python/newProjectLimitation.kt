// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel

/**
 * We do not support new projects on any eel but local.
 * To support it on WSL2 we would need to rethink the whole V2 UI, and for Docker it doesn't make any sence.
 */
@Suppress("LocalEelUsage")
internal val EEL_FOR_NEW_PROJECTS: EelApi get() = localEel
