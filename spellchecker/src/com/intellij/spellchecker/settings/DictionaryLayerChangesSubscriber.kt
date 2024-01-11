// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.settings

import com.intellij.openapi.components.Service
import com.intellij.util.application
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic

@Service(Service.Level.PROJECT)
class DictionaryLayersChangesDispatcher {
  @Topic.ProjectLevel
  private val topic = Topic(DictionaryLayerChangesSubscriber::class.java)

  val publisher: DictionaryLayerChangesSubscriber
    get() = application.messageBus.syncPublisher(topic)

  fun register(subscriber: DictionaryLayerChangesSubscriber): MessageBusConnection {
    val connection = application.messageBus.connect()
    connection.subscribe(topic, subscriber)
    return connection
  }
}

interface DictionaryLayerChangesSubscriber {
  fun layersChanged()
}