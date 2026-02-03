// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.common.kotlin

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.uast.test.common.kotlin.UastTestSuffix.FE10_SUFFIX
import org.jetbrains.uast.test.common.kotlin.UastTestSuffix.FIR_SUFFIX

internal val ExpectedPluginModeProvider.pluginSuffix: String
    get() = pluginMode.pluginSuffix

internal val ExpectedPluginModeProvider.counterpartSuffix: String
    get() = pluginMode.other.pluginSuffix

private val KotlinPluginMode.pluginSuffix: String
    get() = when (this) {
        KotlinPluginMode.K1 -> FE10_SUFFIX
        KotlinPluginMode.K2 -> FIR_SUFFIX
    }