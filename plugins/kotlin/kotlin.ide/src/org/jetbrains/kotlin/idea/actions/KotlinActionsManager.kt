// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions

import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer

private const val NEW_DIR: String = "NewDir"

private class KotlinActionsManager: ActionConfigurationCustomizer, ActionConfigurationCustomizer.LightCustomizeStrategy {
    override suspend fun customize(actionRegistrar: ActionRuntimeRegistrar) {
        actionRegistrar.replaceAction(NEW_DIR, CreateKotlinAwareDirectoryOrPackageAction())
    }
}