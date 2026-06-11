// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.api

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
data class WebViewCreationOptions(
  val enginePreference: WebViewEnginePreference = WebViewEnginePreference.System,
  val requirements: WebViewEngineRequirements = WebViewEngineRequirements(),
  val debugName: String? = null,
)

@ApiStatus.Experimental
data class WebViewRuntimeInfo(
  val engineId: WebViewEngineId,
  val capabilities: WebViewEngineCapabilities,
  val displayName: String,
)

@ApiStatus.Experimental
data class WebViewEngineRequirements(
  val assetServing: Boolean = false,
  val messagePassing: Boolean = false,
  val swingEmbedding: Boolean = false,
  val interactiveInput: Boolean = false,
)

@ApiStatus.Experimental
data class WebViewEngineCapabilities(
  val assetServing: Boolean,
  val messagePassing: Boolean,
  val swingEmbedding: Boolean,
  val interactiveInput: Boolean,
) {
  fun satisfies(requirements: WebViewEngineRequirements): Boolean {
    return (!requirements.assetServing || assetServing) &&
           (!requirements.messagePassing || messagePassing) &&
           (!requirements.swingEmbedding || swingEmbedding) &&
           (!requirements.interactiveInput || interactiveInput)
  }

  internal fun missingRequirements(requirements: WebViewEngineRequirements): List<String> {
    return buildList {
      if (requirements.assetServing && !assetServing) add("assetServing")
      if (requirements.messagePassing && !messagePassing) add("messagePassing")
      if (requirements.swingEmbedding && !swingEmbedding) add("swingEmbedding")
      if (requirements.interactiveInput && !interactiveInput) add("interactiveInput")
    }
  }
}

@ApiStatus.Experimental
enum class WebViewEnginePreference {
  System,
  Jcef,
}

@ApiStatus.Experimental
@JvmInline
value class WebViewEngineId(val value: String) {
  override fun toString(): String = value

  companion object {
    val SYSTEM_MACOS: WebViewEngineId = WebViewEngineId("SYSTEM_MACOS")
    val SYSTEM_WINDOWS: WebViewEngineId = WebViewEngineId("SYSTEM_WINDOWS")
    val SYSTEM_LINUX: WebViewEngineId = WebViewEngineId("SYSTEM_LINUX")
    val JCEF: WebViewEngineId = WebViewEngineId("JCEF")
  }
}

@ApiStatus.Experimental
sealed class WebViewEngineAvailability private constructor() {
  data object Available : WebViewEngineAvailability()
  data class Unavailable(val reason: String) : WebViewEngineAvailability()
}
