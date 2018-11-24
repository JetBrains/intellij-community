/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.UrlOpener
import com.intellij.ide.browsers.WebBrowser
import com.intellij.openapi.project.Project

internal class DefaultUrlOpener : UrlOpener() {
  override fun openUrl(browser: WebBrowser, url: String, project: Project?): Boolean {
    return BrowserLauncher.instance.browseUsingPath(url, browser = browser, project = project)
  }
}