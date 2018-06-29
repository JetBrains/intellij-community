/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.browsers.impl

import com.intellij.ide.browsers.OpenInBrowserRequest
import com.intellij.ide.browsers.WebBrowserService
import com.intellij.ide.browsers.WebBrowserUrlProvider
import com.intellij.ide.browsers.createOpenInBrowserRequest
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.containers.ContainerUtil
import com.intellij.xml.util.HtmlUtil

class WebBrowserServiceImpl : WebBrowserService() {
  companion object {
    fun getProvider(request: OpenInBrowserRequest): WebBrowserUrlProvider? {
      val dumbService = DumbService.getInstance(request.project)
      for (urlProvider in WebBrowserUrlProvider.EP_NAME.extensions) {
        if ((!dumbService.isDumb || DumbService.isDumbAware(urlProvider)) && urlProvider.canHandleElement(request)) {
          return urlProvider
        }
      }
      return null
    }

    fun getDebuggableUrls(context: PsiElement?): Collection<Url> {
      try {
        val request = if (context == null) null else createOpenInBrowserRequest(context)
        if (request == null || request.file.viewProvider.baseLanguage === XMLLanguage.INSTANCE) {
          return emptyList()
        }
        else {
          // it is client responsibility to set token
          request.isAppendAccessToken = false
          return getUrls(getProvider(request), request)
        }
      }
      catch (ignored: WebBrowserUrlProvider.BrowserException) {
        return emptyList()
      }
    }

    @JvmStatic
    fun getDebuggableUrl(context: PsiElement?): Url? = ContainerUtil.getFirstItem(getDebuggableUrls(context))
  }

  override fun getUrlsToOpen(request: OpenInBrowserRequest, preferLocalUrl: Boolean): Collection<Url> {
    val isHtmlOrXml = WebBrowserService.isHtmlOrXmlFile(request.file)
    if (!preferLocalUrl || !isHtmlOrXml) {
      val dumbService = DumbService.getInstance(request.project)
      for (urlProvider in WebBrowserUrlProvider.EP_NAME.extensions) {
        if ((!dumbService.isDumb || DumbService.isDumbAware(urlProvider)) && urlProvider.canHandleElement(request)) {
          val urls = getUrls(urlProvider, request)
          if (!urls.isEmpty()) {
            return urls
          }
        }
      }

      if (!isHtmlOrXml) {
        return emptyList()
      }
    }

    val file = if (!request.file.viewProvider.isPhysical) null else request.virtualFile
    return if (file is LightVirtualFile || file == null) emptyList() else listOf(Urls.newFromVirtualFile(file))
  }
}

private fun getUrls(provider: WebBrowserUrlProvider?, request: OpenInBrowserRequest): Collection<Url> {
  if (provider != null) {
    request.result?.let { return it }

    try {
      return provider.getUrls(request)
    }
    catch (e: WebBrowserUrlProvider.BrowserException) {
      if (!HtmlUtil.isHtmlFile(request.file)) {
        throw e
      }
    }
  }
  return emptyList()
}