// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver.dataextractor

import com.jetbrains.performancePlugin.remotedriver.LruCache
import java.util.*
import java.util.function.BiConsumer
import java.util.function.Consumer

internal object TextToKeyCache {
  private val textToKeyMap = Collections.synchronizedMap(LruCache<String, MutableSet<String>>(10_000))

  fun init(ideClassLoader: ClassLoader) {
    val bundleClass = Class.forName("com.intellij.BundleBase", true, ideClassLoader)

    // 213
    val setTranslationConsumerFunction = try {
      bundleClass.getMethod("setTranslationConsumer", BiConsumer::class.java)
    }
    catch (e: Throwable) {
      null
    }
    if (setTranslationConsumerFunction != null) {
      val consumer = BiConsumer<String, String> { key, t ->
        val text = removeMnemonic(ideClassLoader, t)
        if (textToKeyMap.containsKey(text).not()) {
          textToKeyMap[text] = mutableSetOf()
        }
        textToKeyMap[text]?.add(key)
      }
      setTranslationConsumerFunction.invoke(null, consumer)
    }
    else {
      // 212
      val messageCallConsumerListField = try {
        bundleClass.getField("translationConsumerList")
      }
      catch (e: Throwable) {
        null
      }
      if (messageCallConsumerListField != null) {
        val list =
          messageCallConsumerListField.get(null) as MutableList<Consumer<*>>
        list.add {
          val key = it.javaClass.getDeclaredField("first").get(it) as String
          val value = it.javaClass.getDeclaredField("second").get(it) as String
          val text = removeMnemonic(ideClassLoader, value)
          if (textToKeyMap.containsKey(text).not()) {
            textToKeyMap[text] = mutableSetOf()
          }
          textToKeyMap[text]?.add(key)
        }
      }
    }
  }

  fun findKey(text: String): String? {
    var key: Set<String>? = textToKeyMap[text]
    if (key == null && text.endsWith("...")) {
      synchronized(textToKeyMap) {
        key = textToKeyMap[textToKeyMap.keys.firstOrNull { it.startsWith(text.split("...")[0]) }]
      }
    }
    if (key == null && text.contains("(")) {
      key = textToKeyMap[text.split("(")[0].trim()]
    }
    return key?.joinToString(" ") { it }
  }

  private fun removeMnemonic(ideClassLoader: ClassLoader, str: String): String =
    Class.forName("com.intellij.util.ui.UIUtil", true, ideClassLoader)
      .getDeclaredMethod("removeMnemonic", String::class.java)
      .invoke(null, str) as String
}