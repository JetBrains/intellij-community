// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xray.frontend.impl.platform

import com.intellij.codeInsight.hints.settings.InlaySettingsConfigurableFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project

internal class XRayInlaySettingsConfigurableFactory: InlaySettingsConfigurableFactory {
  override fun create(project: Project): Configurable = XRayInlaySettingsConfigurable(project)
}
