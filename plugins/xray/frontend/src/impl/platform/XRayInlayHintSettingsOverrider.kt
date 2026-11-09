// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xray.frontend.impl.platform

import com.intellij.codeInsight.hints.InlayHintSettingsKey
import com.intellij.codeInsight.hints.InlayHintSettingsOverrider
import com.intellij.xray.frontend.impl.service.XRayModeService
import com.intellij.xray.frontend.impl.service.XRayStageStore

internal class XRayInlayHintSettingsOverrider : InlayHintSettingsOverrider {
  override fun isEnabled(key: InlayHintSettingsKey): Boolean? {
    val service = XRayModeService.getInstance()
    val store = XRayStageStore.getInstanceOrNull() ?: return null

    val storedStage = store.findStage(key)
    return storedStage?.isVisibleIn(service.getXRayModeState())
  }
}
