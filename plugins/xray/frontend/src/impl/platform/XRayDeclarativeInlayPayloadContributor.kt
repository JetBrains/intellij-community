// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xray.frontend.impl.platform

import com.intellij.codeInsight.hints.declarative.InlayPayload
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayPayloadContributor

/** The name of the payload that holds the innermost option ID of a declarative inlay hint. */
internal const val XRAY_OPTION_ID_PAYLOAD: String = "xray.option.id"

internal class XRayDeclarativeInlayPayloadContributor : DeclarativeInlayPayloadContributor {
  override fun contribute(activeOptionIds: List<String>): InlayPayload? {
    val optionId = activeOptionIds.lastOrNull() ?: return null
    return InlayPayload(XRAY_OPTION_ID_PAYLOAD, StringInlayActionPayload(optionId))
  }
}
