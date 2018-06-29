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
package com.intellij.ide.browsers

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Url

abstract class WebBrowserUrlProvider {
  companion object {
    @JvmStatic
    val EP_NAME: ExtensionPointName<WebBrowserUrlProvider> = ExtensionPointName.create<WebBrowserUrlProvider>("com.intellij.webBrowserUrlProvider")
  }

  /**
   * Browser exceptions are printed in Error Dialog when user presses any browser button
   */
  class BrowserException(message: String) : Exception(message)

  open fun canHandleElement(request: OpenInBrowserRequest): Boolean {
    val urls = try {
      getUrls(request)
    }
    catch (ignored: BrowserException) {
      return false
    }

    if (!urls.isEmpty()) {
      request.result = urls
      return true
    }
    return false
  }

  @Throws(BrowserException::class)
  protected open fun getUrl(request: OpenInBrowserRequest, file: VirtualFile): Url? {
    return null
  }

  @Throws(BrowserException::class)
  open fun getUrls(request: OpenInBrowserRequest): Collection<Url> = listOfNotNull(request.virtualFile?.let { getUrl(request, it) })
}