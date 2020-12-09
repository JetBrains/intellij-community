// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.auth

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk.*
import com.intellij.space.messages.SpaceBundle
import com.intellij.ui.AppUIUtil
import java.io.File

internal fun createAuthPage(server: String): String = html()
  .child(
    head()
      .child(styleTag(loadAuthResourceFileContent("spaceAuthPage.css")))
  )
  .child(
    body().child(
      div().attr("class", "central-div")
        .child(
          div().attr("class", "central-div-header")
            .child(
              div().attr("class", "central-div-header-icon").addRaw(loadAuthResourceFileContent("spaceAuthIcon.svg"))
            )
            .child(
              div().attr("class", "central-div-header-text").addText(SpaceBundle.message("auth.page.jetbrains.space.header.text"))
            )
        )
        .child(
          div().attr("class", "central-div-content")
            .child(
              div().attr("class", "central-div-content-icon").addRaw(loadProductSvg() ?: "")
            )
            .child(
              div().attr("class", "central-div-content-text").addText(SpaceBundle.message("auth.page.content.text"))
            )
        )
        .child(
          link(server, SpaceBundle.message("auth.page.go.to.space.link")).attr("class", "central-div-content-link")
        )
    )
  )
  .toString()

@NlsSafe
private fun loadAuthResourceFileContent(fileName: String): String {
  val plugin = PluginManagerCore.getPlugin(PluginId.getId("com.jetbrains.space"))
               ?: throw IllegalStateException("Couldn't find space plugin descriptor")
  val loader = plugin.pluginClassLoader
  return loader.getResourceAsStream("auth/$fileName")!!.bufferedReader(Charsets.UTF_8).readText()
}

@NlsSafe
private fun loadProductSvg(): String? {
  AppUIUtil.findIcon().takeIf { it?.endsWith(".svg") ?: false }?.let { iconPath ->
    val iconFile = File(iconPath)
    if (iconFile.exists() && iconFile.isFile) {
      return iconFile.readText(Charsets.UTF_8)
    }
  }
  return null
}