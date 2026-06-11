package com.intellij.python.lsp.core.listener

import com.intellij.util.messages.Topic

interface PyLspListener {
  fun onTypeSettingsChange()

  companion object {
    val TOPIC: Topic<PyLspListener> = Topic.create("Topic.PyLspListener", PyLspListener::class.java)
  }
}