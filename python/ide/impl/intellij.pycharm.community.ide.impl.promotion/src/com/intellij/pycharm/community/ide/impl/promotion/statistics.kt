// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion

import com.intellij.ide.BrowserUtil
import com.intellij.ide.customization.UtmIdeUrlTrackingParametersProvider
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import com.intellij.pycharm.community.ide.impl.promotion.PyCharmPromoCollector.PromoLearnModeEvent
import com.intellij.pycharm.community.ide.impl.promotion.PyCharmPromoCollector.PromoOpenDownloadPageEvent
import org.apache.http.client.utils.URIBuilder
import java.net.URISyntaxException

object PyCharmPromoCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private const val FUS_GROUP_ID = "pycharm.promo"
  private val GROUP = EventLogGroup(FUS_GROUP_ID, 1)
  private val PromoEventSourceField = EventFields.Enum("source", PromoEventSource::class.java) { it.name.lowercase() }
  private val PromoTopicField = EventFields.Enum("topic", PromoTopic::class.java) { it.name.lowercase() }
  internal val PromoOpenDownloadPageEvent = GROUP.registerEvent("open.download.page", PromoEventSourceField, PromoTopicField)
  internal val PromoLearnModeEvent = GROUP.registerEvent("open.learn.more.page", PromoEventSourceField, PromoTopicField)
}

internal enum class PromoEventSource {
  GO_TO_ACTION,
  NEW_FILE,
  PROJECT_WIZARD,
  SETTINGS,
}

internal enum class PromoTopic {
  AiCodeCompletion,
  Database,
  Dataframe,
  Django,
  Docker,
  Endpoints,
  JavaScript,
  Jupyter,
  Plots,
  RemoteSSH,
  TypeScript,
}

internal fun createOpenDownloadPageLambda(promoEventSource: PromoEventSource, promoTopic: PromoTopic): () -> Unit = {
  BrowserUtil.browse(createLinkWithInfo(promoTopic, PluginAdvertiserService.pyCharmProfessional.downloadUrl))
  PromoOpenDownloadPageEvent.log(promoEventSource, promoTopic)
}

internal fun createOpenLearnMorePageLambda(promoEventSource: PromoEventSource, promoTopic: PromoTopic): (String) -> Unit = { url ->
  BrowserUtil.browse(createLinkWithInfo(promoTopic, url))
  PromoLearnModeEvent.log(promoEventSource, promoTopic)
}

private fun createLinkWithInfo(promoTopic: PromoTopic, originalUrl: String): String {
  try {
    return URIBuilder(originalUrl)
      .setParameter("utm_source", "product")
      .setParameter("utm_medium", "link")
      .setParameter("utm_campaign", ApplicationInfo.getInstance().getBuild().productCode)
      .setParameter("utm_content", ApplicationInfo.getInstance().shortVersion)
      .setParameter("utm_term", promoTopic.name.lowercase())
      .build().toString()
  }
  catch (e: URISyntaxException) {
    Logger.getInstance(UtmIdeUrlTrackingParametersProvider::class.java).warn(originalUrl, e)
    return originalUrl
  }
}

