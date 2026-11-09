// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xray.frontend.impl.action

import com.intellij.codeInsight.hints.CodeVisionInlayHintSettingsKey
import com.intellij.codeInsight.hints.DeclarativeInlayHintSettingsKey
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.InlayHintSettingsKey
import com.intellij.codeInsight.hints.LegacyInlayHintSettingsKey
import com.intellij.codeInsight.hints.ParameterHintSettingsKey
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider.Companion.INLAY_PAYLOADS
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider.Companion.PROVIDER_ID
import com.intellij.codeInsight.hints.declarative.InlayHintsProviderExtensionBean
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.codeInsight.hints.settings.language.ParameterInlayProviderSettingsModel
import com.intellij.codeInsight.hints.settings.showInlaySettings
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.util.asSafely
import com.intellij.xray.frontend.api.InlayHintStage
import com.intellij.xray.frontend.api.XRayBundle
import com.intellij.xray.frontend.impl.platform.XRAY_OPTION_ID_PAYLOAD
import com.intellij.xray.frontend.impl.service.XRayModeService
import com.intellij.xray.frontend.impl.service.XRayStageStore

internal fun showSettingsFor(project: Project, key: InlayHintSettingsKey): Boolean = when (key) {
  is ParameterHintSettingsKey ->
    showInlaySettings(project, key.language) { it.id == ParameterInlayProviderSettingsModel.ID }
  is LegacyInlayHintSettingsKey ->
    key.language?.let { lang ->
      showInlaySettings(project, lang) { it.id == key.providerId }
    } ?: false
  is DeclarativeInlayHintSettingsKey -> {
    val language = InlayHintsProviderExtensionBean.EP.extensionList
      .firstOrNull { it.requiredProviderId() == key.providerId }
      ?.language?.let { Language.findLanguageByID(it) }
    language?.let { showInlaySettings(project, it) { m -> m.id == key.providerId } } ?: false
  }
  is CodeVisionInlayHintSettingsKey -> showInlaySettings(project, Language.ANY) {
    it.group == InlayGroup.CODE_VISION_GROUP_NEW && it.id == key.groupId
  }
}

internal class XRayHintVisibilityGroup(
  private val key: InlayHintSettingsKey? = null
) : ActionGroup(), DumbAware {

  init {
    this.isPopup = true
    this.templatePresentation.text = XRayBundle.message("xray.inlay.menu.change")
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private class XRaySetHintStageAction(
    private val key: InlayHintSettingsKey,
    private val stage: InlayHintStage,
    @NlsActions.ActionText title: String,
  ) : DumbAwareAction(title) {

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      val store = XRayStageStore.getInstanceOrNull() ?: return
      val service = XRayModeService.getInstance()

      val previousStage = store.getStage(key)

      val editor = e.getData(CommonDataKeys.EDITOR)

      if (editor == null) {
        if (IdeProductMode.isFrontend) return
        store.setStage(key, stage)
        service.refreshAllHints()
        return
      }

      applyStageChange(editor, key, stage, previousStage) {
        showSettingsFor(project, key)
      }
    }

    constructor(key: InlayHintSettingsKey, choice: XRayStageChoice) : this(key, choice.stage, choice.title)
  }

  private fun resolveInlayHintKey(e: AnActionEvent?): InlayHintSettingsKey? {
    val event = e ?: return null
    val language = event.getData(CommonDataKeys.PSI_FILE)?.language ?: return null

    return when (key) {
      null -> {
        val providerId = event.getData(PROVIDER_ID) ?: return null
        val optionId = event.getData(INLAY_PAYLOADS)?.get(XRAY_OPTION_ID_PAYLOAD).asSafely<StringInlayActionPayload>()?.text
        DeclarativeInlayHintSettingsKey(providerId, optionId)
      }
      is DeclarativeInlayHintSettingsKey -> key
      is LegacyInlayHintSettingsKey -> key.copy(language = key.language ?: language)
      is CodeVisionInlayHintSettingsKey -> key
      is ParameterHintSettingsKey -> if (key.optionId == null) null else key
    }
  }

  override fun getChildren(e: AnActionEvent?): Array<out AnAction> {
    val key = resolveInlayHintKey(e) ?: return emptyArray()
    val store = XRayStageStore.getInstanceOrNull() ?: return emptyArray()
    val currentStage = store.getStage(key)
    // InlayHintStage.entries runs most-visible (ALWAYS) to least-visible (DISABLED), matching the menu order.
    val actions = xrayStageChoices(currentStage)
      .map { XRaySetHintStageAction(key, it) }
      .toMutableList<AnAction>()
    actions.add(Separator.getInstance())
    actions.add(XRayNavigateToSettingsAction(key))
    return actions.toTypedArray()
  }

  override fun update(e: AnActionEvent) {
    val key = resolveInlayHintKey(e)
    val store = XRayStageStore.getInstanceOrNull() ?: return
    if (key != null) {
      e.presentation.isEnabledAndVisible = true
      e.presentation.text = XRayBundle.message("xray.inlay.menu.change")
      e.presentation.putClientProperty(ActionUtil.POPUP_AD_TEXT, stageVisibilitySummary(store.getStage(key)))
    }
    else {
      e.presentation.isEnabledAndVisible = false
    }
  }
}
