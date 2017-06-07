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
package com.intellij.ide.browsers

import com.intellij.concurrency.JobScheduler
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.showOkNoDialog
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.AppUIUtil
import com.intellij.util.Urls
import org.jetbrains.ide.BuiltInServerManager
import java.util.concurrent.TimeUnit

class BrowserLauncherImpl : BrowserLauncherAppless() {
  override fun getEffectiveBrowser(browser: WebBrowser?): WebBrowser? {
    var effectiveBrowser = browser
    if (browser == null) {
      // https://youtrack.jetbrains.com/issue/WEB-26547
      val browserManager = WebBrowserManager.getInstance()
      if (browserManager.getDefaultBrowserPolicy() == DefaultBrowserPolicy.FIRST) {
        effectiveBrowser = browserManager.firstActiveBrowser
      }
    }
    return effectiveBrowser
  }

  override fun signUrl(url: String): String {
    @Suppress("NAME_SHADOWING")
    var url = url
    @Suppress("NAME_SHADOWING")
    val serverManager = BuiltInServerManager.getInstance()
    val parsedUrl = Urls.parse(url, false)
    if (parsedUrl != null && serverManager.isOnBuiltInWebServer(parsedUrl)) {
      if (Registry.`is`("ide.built.in.web.server.activatable", false)) {
        PropertiesComponent.getInstance().setValue("ide.built.in.web.server.active", true)
      }

      url = serverManager.addAuthToken(parsedUrl).toExternalForm()
    }
    return url
  }

  override fun browseUsingNotSystemDefaultBrowserPolicy(url: String, settings: GeneralSettings, project: Project?) {
    val browserManager = WebBrowserManager.getInstance()
    if (browserManager.getDefaultBrowserPolicy() == DefaultBrowserPolicy.FIRST) {
      browserManager.firstActiveBrowser?.let {
        browse(url, it, project)
        return
      }
    }
    else if (SystemInfo.isMac && "open" == settings.browserPath) {
      browserManager.firstActiveBrowser?.let {
        browseUsingPath(url, null, it, project)
        return
      }
    }

    super.browseUsingNotSystemDefaultBrowserPolicy(url, settings, project)
  }

  override fun showError(error: String?, browser: WebBrowser?, project: Project?, title: String?, launchTask: (() -> Unit)?) {
    AppUIUtil.invokeOnEdt(Runnable {
      if (!showOkNoDialog(title ?: IdeBundle.message("browser.error"), error ?: "Unknown error", project, noText = IdeBundle.message("button.fix"))) {
        val browserSettings = BrowserSettings()
        if (ShowSettingsUtil.getInstance().editConfigurable(project, browserSettings, browser?.let { Runnable { browserSettings.selectBrowser(it) } })) {
          launchTask?.invoke()
        }
      }
    }, project?.disposed)
  }

  override fun checkCreatedProcess(browser: WebBrowser?, project: Project?, commandLine: GeneralCommandLine, process: Process, launchTask: (() -> Unit)?) {
    if (isOpenCommandUsed(commandLine)) {
      val future = ApplicationManager.getApplication().executeOnPooledThread {
        try {
          if (process.waitFor() == 1) {
            showError(ExecUtil.readFirstLine(process.errorStream, null), browser, project, null, launchTask)
          }
        }
        catch (ignored: InterruptedException) {
        }
      }
      // 10 seconds is enough to start
      JobScheduler.getScheduler().schedule({ future.cancel(true) }, 10, TimeUnit.SECONDS)
    }
  }
}