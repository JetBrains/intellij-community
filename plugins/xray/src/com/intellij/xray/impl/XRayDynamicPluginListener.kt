// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xray.impl

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.openapi.editor.EditorFactory
import com.intellij.xray.impl.editorTracking.XRayModeTracker

internal class XRayDynamicPluginListener: DynamicPluginListener {
  override fun pluginsLoaded() {
    val tracker = XRayModeTracker()
    EditorFactory.getInstance().allEditors.forEach { editor ->
      tracker.attach(editor)
    }
  }
}
