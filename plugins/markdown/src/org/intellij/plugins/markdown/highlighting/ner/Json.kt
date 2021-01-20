// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.highlighting.ner

import com.google.gson.Gson

internal object Json {
  private val gson = Gson()

  fun <T> stringify(value: T): String {
    return gson.toJson(value)
  }

  inline fun <reified T> parse(json: String): T? {
    return gson.fromJson(json, T::class.java)
  }
}