// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.core.entity

import org.jetbrains.kotlin.tools.projectWizard.core.safeAs

interface Entity {
    val path: String
}
abstract class EntityBase: Entity {
    final override fun equals(other: Any?): Boolean = other.safeAs<Entity>()?.path == path
    final override fun hashCode(): Int = path.hashCode()
    final override fun toString(): String = path
}

abstract class EntityWithValue<out T : Any> : EntityBase()
