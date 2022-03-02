// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.authorization

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk.*
import com.intellij.ui.AppUIUtil
import org.intellij.plugins.markdown.MarkdownBundle
import java.io.File

object AuthResultPage {
  fun createAuthPage(isSuccess: Boolean): String = html()
    .children(
      head().child(styleTag(loadFileFromResource("googleAuth/googleAuthPage.css") ?: "")),
      body().child(div().attr("class", "central-div-panel").children(createPanelHeader(), createPanelContent(isSuccess)))
    ).toString()

  private fun createPanelHeader() = div()
    .attr("class", "central-div-header")
    .child(
      div().attr("class", "central-div-header-text").addText("JetBrains")
    )

  private fun createPanelContent(isSuccess: Boolean) = div()
    .attr("class", "central-div-content")
    .children(
      getApplicationIcon(),
      getMessageElement(isSuccess)
    )

  private fun getApplicationIcon(): Element {
    val icon: @NlsSafe String? = AppUIUtil.findIcon()
      .takeIf { it?.endsWith(".svg") ?: false }
      ?.let {
        val iconFile = File(it)

        if (iconFile.exists() && iconFile.isFile) iconFile.readText(Charsets.UTF_8)
        else null
      }

    return div().attr("class", "central-div-app-icon").addRaw(icon ?: "")
  }

  private fun getMessageElement(isSuccess: Boolean): Element {
    val text = if (isSuccess) MarkdownBundle.message("markdown.google.auth.result.success")
    else MarkdownBundle.message("markdown.google.auth.result.failed")

    val icon = if (isSuccess) loadFileFromResource("googleAuth/success.svg") else loadFileFromResource("googleAuth/error.svg")

    return div()
      .attr("class", "central-div-content")
      .children(
        div().attr("class", "central-div-content-icon").addRaw(icon ?: ""),
        div().attr("class", "central-div-content-text").addText(text)
      )
  }

  @NlsSafe
  private fun loadFileFromResource(path: String): String? {
    val plugin = PluginManagerCore.getPlugin(PluginId.getId("org.intellij.plugins.markdown"))
                 ?: throw IllegalStateException("Couldn't find markdown plugin descriptor")
    val loader = plugin.pluginClassLoader

    return loader?.getResourceAsStream(path)?.bufferedReader(Charsets.UTF_8)?.readText()
  }
}
