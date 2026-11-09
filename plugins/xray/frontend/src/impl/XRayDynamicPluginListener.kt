// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xray.frontend.impl

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.xray.frontend.impl.editorTracking.attachXRayTracker
import com.intellij.xray.frontend.impl.editorTracking.detachXRayTracker

private const val XRAY_PLUGIN_ID: String = "com.intellij.xray"

internal class XRayDynamicPluginListener: DynamicPluginListener {

  override fun pluginsLoaded() {
    EditorFactory.getInstance().allEditors.forEach { editor ->
      attachXRayTracker(editor)
    }
  }

  override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
    if (pluginDescriptor.pluginId.idString == XRAY_PLUGIN_ID) {
      EditorFactory.getInstance().allEditors.forEach { editor ->
        detachXRayTracker(editor)
      }
    }
  }
}
