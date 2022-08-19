// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.testGenerator.model

import kotlin.reflect.KClass

fun importsListOf(vararg classes: KClass<*>): List<String> {
    return classes.map { it.java.canonicalName }
}

interface TWorkspace {
    val groups: List<TGroup>
}

interface MutableTWorkspace : TWorkspace {
    override val groups: MutableList<TGroup>
}

class TWorkspaceImpl : MutableTWorkspace {
    override val groups = mutableListOf<TGroup>()
}

fun workspace(block: MutableTWorkspace.() -> Unit): TWorkspace {
    return TWorkspaceImpl().apply(block)
}