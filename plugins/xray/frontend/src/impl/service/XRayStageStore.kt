// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xray.frontend.impl.service

import com.intellij.codeInsight.hints.CodeVisionInlayHintSettingsKey
import com.intellij.codeInsight.hints.DeclarativeInlayHintSettingsKey
import com.intellij.codeInsight.hints.InlayHintSettingsKey
import com.intellij.codeInsight.hints.LegacyInlayHintSettingsKey
import com.intellij.codeInsight.hints.ParameterHintSettingsKey
import com.intellij.configurationStore.deserialize
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.xray.frontend.api.InlayHintStage

private val LOG = logger<XRayStageStore>()

@Service(Service.Level.APP)
@State(name = "XRayStageSettings", storages = [Storage("xray_stage_settings.xml")])
internal class XRayStageStore : SimplePersistentStateComponent<XRayStageStore.State>(State()) {

  private val defaultStages: Map<String, InlayHintStage> = loadDefaultStages()
  private fun loadDefaultStages(): Map<String, InlayHintStage> {
    val classLoader = XRayStageStore::class.java.classLoader ?: return emptyMap()
    val result = mutableMapOf<String, InlayHintStage>()
    try {
      val indexUrl =
        classLoader.getResource("xray/defaults/index.xml") ?: run { LOG.warn("Missing default stages index"); return emptyMap() }
      val defaultsResources: List<String> = JDOMUtil.load(indexUrl)
        .getChildren("entry").mapNotNull { it.getAttributeValue("value") }
      defaultsResources.forEach { path ->
        try {
          val url = classLoader.getResource(path) ?: run { LOG.warn("Missing default stages: $path"); return@forEach }
          result += JDOMUtil.load(url).deserialize(State::class.java).stages
        }
        catch (e: Exception) {
          LOG.warn("Unable to load default stages: $path", e)
        }
      }
    }
    catch (e: Exception) {
      LOG.warn("Unable to list default stages", e)
      return emptyMap()
    }
    return result
  }

  internal class State : BaseState() {
    var stages: MutableMap<String, InlayHintStage> by map()
  }

  fun getStage(key: InlayHintSettingsKey): InlayHintStage {
    val k = keyOf(key)
    return state.stages[k] ?: defaultStages[k] ?: InlayHintStage.ALWAYS
  }

  fun setStage(key: InlayHintSettingsKey, stage: InlayHintStage) {
    state.stages[keyOf(key)] = stage
    state.intIncrementModificationCount()
  }

  fun findStage(key: InlayHintSettingsKey): InlayHintStage? {
    val k = keyOf(key)
    return state.stages[k] ?: defaultStages[k]
  }

  /**
   * Generates a key from the [InlayHintSettingsKey]
   *
   * Key generation follows the format:
   * - CodeVision inlay hints: "CODE_VISION|<GROUP_ID>"
   * - Legacy inlay hints: "LEGACY|<LANGUAGE_ID>|<PROVIDER_ID>"
   * - Declarative inlay hints: "DECLARATIVE|<PROVIDER_ID>|<OPTION_ID>"
   * - Parameter inlay hints: "PARAMETER_HINT|<LANGUAGE>|<OPTION_ID>"
   */
  private fun keyOf(key: InlayHintSettingsKey): String {
    return when (key) {
      is CodeVisionInlayHintSettingsKey -> "CODE_VISION|${key.groupId}"
      is LegacyInlayHintSettingsKey -> "LEGACY|${key.language?.id.orEmpty()}|${key.providerId}"
      is DeclarativeInlayHintSettingsKey -> "DECLARATIVE|${key.providerId}|${key.optionId.orEmpty()}"
      is ParameterHintSettingsKey -> "PARAMETER_HINT|${key.language.id}|${key.optionId.orEmpty()}"
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): XRayStageStore = ApplicationManager.getApplication().service()

    @JvmStatic
    fun getInstanceOrNull(): XRayStageStore? = ApplicationManager.getApplication().serviceOrNull()
  }
}
