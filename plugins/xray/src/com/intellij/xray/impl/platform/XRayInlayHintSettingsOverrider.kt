// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xray.impl.platform

import com.intellij.codeInsight.hints.InlayHintSettingsOverrider
import com.intellij.codeInsight.hints.InlayHintSettingsRequest
import com.intellij.xray.impl.service.XRayModeService
import com.intellij.xray.impl.service.XRayStageStore

internal class XRayInlayHintSettingsOverrider : InlayHintSettingsOverrider {
  override fun isEnabled(request: InlayHintSettingsRequest): Boolean? {
    val service = XRayModeService.getInstance(request.project)
    val store = XRayStageStore.getInstanceOrNull() ?: return null

    val storedStage = store.findStage(request.settingsKey)
    return storedStage?.isVisibleIn(service.getXRayModeState())
  }
}
