// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xray.frontend.impl.platform

import com.intellij.codeInsight.hints.InlayContextMenuContributor
import com.intellij.codeInsight.hints.InlayHintSettingsKey
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.xray.frontend.impl.action.XRayHintVisibilityGroup

internal class XRayInlayContextMenuContributor : InlayContextMenuContributor {
  override fun contribute(key: InlayHintSettingsKey): ActionGroup {
    return XRayHintVisibilityGroup(key)
  }
}
