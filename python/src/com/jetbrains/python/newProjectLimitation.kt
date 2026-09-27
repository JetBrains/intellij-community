// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.localEel

/**
 * We do not support new projects on any eel but local in Idea, so for Idea this is the only supported eel.
 * For PyCharm it is just a default
 */
@Suppress("LocalEelUsage")
internal val DEFAULT_EEL_FOR_NEW_PROJECTS: EelApi get() = localEel
