// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.whatsnew

import com.intellij.platform.whatsNew.ContentSource
import com.intellij.platform.whatsNew.ResourceContentSource
import com.intellij.platform.whatsNew.WhatsNewInVisionContentProvider
import com.intellij.util.PlatformUtils.isCommunityEdition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class PyCharmWhatsNewInVisionContentProvider : WhatsNewInVisionContentProvider() {

  companion object {
    const val endpoints = "Check out the Endpoints tool window"
    const val endpointsAction = "ActivateEndpointsToolWindow"
    const val feelTheDifference = "Feel the difference"
    const val feelTheDifferenceAction = "showWelcomeNotebook"
    const val plotly ="See Plotly graphs"
    const val plotlyAction = "ShowPlotlyGraph"
    const val getPyCharmPro = "Get PyCharm Professional: free 30-day trial"
    const val promoEndpointsActionId = "PromoEndpointsAction"
    const val promoNotebookActionId = "PromoNewJupyterNotebook"
  }

  override fun getResource(): ContentSource {
    // return a vision file for the current version
    val resourceContentSource = ResourceContentSource(PyCharmWhatsNewInVisionContentProvider::class.java.classLoader, "whatsNew/pycharm2024.2.json")
    if (isCommunityEdition()) {
      return object : ContentSource {
        override suspend fun openStream(): InputStream? {
          return withContext(Dispatchers.IO) {
            val stream = resourceContentSource.openStream()?.readAllBytes() ?: return@withContext null
            val string = String(stream)
              .replace(endpoints, getPyCharmPro)
              .replace(endpointsAction, promoEndpointsActionId)
              .replace(feelTheDifference, getPyCharmPro)
              .replace(feelTheDifferenceAction, promoNotebookActionId)
              .replace(plotly, getPyCharmPro)
              .replace(plotlyAction, promoNotebookActionId)
            string.byteInputStream()
          }
        }

        override suspend fun checkAvailability(): Boolean {
          return resourceContentSource.checkAvailability()
        }
      }
    }

    return resourceContentSource
  }
}