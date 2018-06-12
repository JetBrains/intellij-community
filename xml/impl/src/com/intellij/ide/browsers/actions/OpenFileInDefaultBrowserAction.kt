/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.browsers.actions

import com.intellij.ide.GeneralSettings
import com.intellij.ide.browsers.BrowserLauncherAppless
import com.intellij.ide.browsers.DefaultBrowserPolicy
import com.intellij.ide.browsers.WebBrowser
import com.intellij.ide.browsers.WebBrowserManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.xml.util.HtmlUtil

class OpenFileInDefaultBrowserAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val result = BaseOpenInBrowserAction.doUpdate(e) ?: return

    var description = templatePresentation.description
    if (HtmlUtil.isHtmlFile(result.file)) {
      description += " (hold Shift to open URL of local file)"
    }

    val presentation = e.presentation
    presentation.text = templatePresentation.text
    presentation.description = description

    findUsingBrowser()?.let {
      presentation.icon = it.icon
    }

    if (ActionPlaces.isPopupPlace(e.place)) {
      presentation.isVisible = presentation.isEnabled
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    BaseOpenInBrowserAction.open(e, findUsingBrowser())
  }
}

fun findUsingBrowser(): WebBrowser? {
  val browserManager = WebBrowserManager.getInstance()
  val defaultBrowserPolicy = browserManager.defaultBrowserPolicy
  if (defaultBrowserPolicy == DefaultBrowserPolicy.FIRST || defaultBrowserPolicy == DefaultBrowserPolicy.SYSTEM && !BrowserLauncherAppless.canUseSystemDefaultBrowserPolicy()) {
    return browserManager.firstActiveBrowser
  }
  else if (defaultBrowserPolicy == DefaultBrowserPolicy.ALTERNATIVE) {
    val path = GeneralSettings.getInstance().browserPath
    if (!path.isNullOrBlank()) {
      val browser = browserManager.findBrowserById(path)
      if (browser == null) {
        for (item in browserManager.activeBrowsers) {
          if (path == item.path) {
            return item
          }
        }
      }
      else {
        return browser
      }
    }
  }
  return null
}