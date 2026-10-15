// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xray.impl.platform

import com.intellij.codeInsight.hints.settings.InlaySettingsConfigurableProvider
import com.intellij.ide.actions.ConfigurablesPatcher
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.project.Project

internal class XRayConfigurablesPatcher : ConfigurablesPatcher {
  override fun modifyOriginalConfigurablesList(originalConfigurables: MutableList<Configurable>, project: Project?) {
    originalConfigurables.removeIf { it is ConfigurableWrapper && it.providerClass == InlaySettingsConfigurableProvider::class.qualifiedName }
  }
}
