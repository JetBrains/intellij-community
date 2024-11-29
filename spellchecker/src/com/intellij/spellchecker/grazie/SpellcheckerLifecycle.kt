// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.grazie

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface SpellcheckerLifecycle {
  suspend fun preload(project: Project)
}

internal val LIFECYCLE_EP_NAME: ExtensionPointName<SpellcheckerLifecycle> = ExtensionPointName("com.intellij.spellchecker.lifecycle")