// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xray.frontend.impl.editorTracking

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
internal class XRayEditorTrackingService: Disposable {

  companion object {
    fun getInstance(): XRayEditorTrackingService = service()
  }

  override fun dispose() {}
}
