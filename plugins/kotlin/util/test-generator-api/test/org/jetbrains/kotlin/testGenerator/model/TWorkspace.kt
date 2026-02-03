// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.testGenerator.model

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

interface TWorkspace {

    val pluginMode: KotlinPluginMode

    val groups: List<TGroup>
}

interface MutableTWorkspace : TWorkspace {

    override val groups: MutableList<TGroup>
}

fun workspace(
    pluginMode: KotlinPluginMode,
    block: MutableTWorkspace.() -> Unit,
): TWorkspace = object : MutableTWorkspace {

    override val pluginMode: KotlinPluginMode
        get() = pluginMode

    override val groups: MutableList<TGroup> =
        mutableListOf()
}.apply(block)