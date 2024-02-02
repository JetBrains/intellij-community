// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.settings

import com.intellij.openapi.components.Service
import com.intellij.util.application
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic

@Service(Service.Level.PROJECT)
class DictionaryLayersChangesDispatcher {
  val publisher: DictionaryLayerChangesListener
    get() = application.messageBus.syncPublisher(DictionaryLayerChangesListener.topic)

  fun register(subscriber: DictionaryLayerChangesListener): MessageBusConnection {
    val connection = application.messageBus.connect()
    connection.subscribe(DictionaryLayerChangesListener.topic, subscriber)
    return connection
  }
}

interface DictionaryLayerChangesListener {
  companion object {
    @Topic.ProjectLevel
    val topic = Topic(DictionaryLayerChangesListener::class.java)
  }

  fun layersChanged()
}