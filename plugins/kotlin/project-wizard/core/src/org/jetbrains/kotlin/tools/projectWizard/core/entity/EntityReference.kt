// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.core.entity

import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.SettingReference
import org.jetbrains.kotlin.tools.projectWizard.core.safeAs

abstract class EntityReference {
    abstract val path: String

    final override fun toString() = path
    final override fun equals(other: Any?) = other.safeAs<SettingReference<*, *>>()?.path == path
    final override fun hashCode() = path.hashCode()
}