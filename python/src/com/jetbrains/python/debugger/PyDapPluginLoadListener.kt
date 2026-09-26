// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
internal class PyDapPluginLoadListener(private val project: Project) : DynamicPluginListener {
  private var isListening = false
  private var onLoaded: Runnable? = null

  init {
    ApplicationManager.getApplication().messageBus.connect(project).subscribe(DynamicPluginListener.TOPIC, this)
  }

  fun listen(onLoaded: Runnable?) {
    isListening = true
    this.onLoaded = onLoaded
  }

  override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
    if (!isListening || pluginDescriptor.pluginId != PluginId.getId(PYTHON_DAP_PLUGIN_ID)) return

    isListening = false
    val callback = onLoaded
    onLoaded = null
    afterInstallSwitchDebugpy()
    callback?.run()
  }
}