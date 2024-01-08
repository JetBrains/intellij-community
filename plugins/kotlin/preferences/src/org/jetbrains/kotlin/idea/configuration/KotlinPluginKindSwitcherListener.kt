// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import java.util.*

interface KotlinPluginKindSwitcherListener : EventListener {
    fun kotlinPluginKindChanged(kotlinPluginMode: KotlinPluginMode)
}