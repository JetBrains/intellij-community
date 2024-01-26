// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.customization

import com.intellij.platform.ide.impl.customization.BaseJetBrainsExternalProductResourceUrls
import com.intellij.util.Url
import com.intellij.util.Urls

class PyCharmExternalResourceUrls : BaseJetBrainsExternalProductResourceUrls() {
  override val productPageUrl: Url
    get() = Urls.newFromEncoded("https://www.jetbrains.com/pycharm/")

  override val basePatchDownloadUrl: Url
    get() = Urls.newFromEncoded("https://download.jetbrains.com/python/")

  override val baseWebHelpUrl: Url
    get() = Urls.newFromEncoded("https://www.jetbrains.com/pycharm/webhelp/")

  override val gettingStartedPageUrl: Url
    get() = Urls.newFromEncoded("https://www.jetbrains.com/pycharm/learn/")

  override val shortProductNameUsedInForms: String
    get() = "PyCharm"

  override val youtrackProjectId: String
    get() = "PY"

  override val useInIdeEvaluationFeedback: Boolean
    get() = true

  override val youTubeChannelUrl: Url
    get() = Urls.newFromEncoded("https://www.youtube.com/playlist?list=PLCTHcU1KoD99eyuXqUJHZy90-9jU2H2Y2")
}