package com.intellij.ide.starter.plugins

import com.intellij.ide.starter.ide.InstalledIde

sealed class PluginSourceDescriptor {
  abstract val pluginId: String
  abstract val channel: String?
  abstract val pluginFileName: String?

  abstract fun downloadUrl(): String

  protected fun pluginIdEscaped(): String = pluginId.replace(" ", "%20")
}

data class PluginLatestForIde(
  override val pluginId: String,
  val ide: InstalledIde,
  override val channel: String? = null,
  override val pluginFileName: String? = null,
) : PluginSourceDescriptor() {

  //example: https://plugins.jetbrains.com/pluginManager/?action=download&id=org.intellij.scala&build=IU-223.SNAPSHOT&channel=nightly&noStatistic=false
  override fun downloadUrl(): String {
    return buildString {
      append("https://plugins.jetbrains.com/pluginManager/?action=download")
      append("&id=${pluginIdEscaped()}")
      append("&noStatistic=true")
      append("&build=${ide.productCode}-${ide.build}")
      channel?.let {
        append("&channel=$it")
      }
    }
  }
}

data class PluginWithExactVersion(
  override val pluginId: String,
  val version: String,
  override val channel: String? = null,
  override val pluginFileName: String? = null,
) : PluginSourceDescriptor() {

  //example: https://plugins.jetbrains.com/plugin/download?pluginId=org.intellij.scala&version=2022.3.703&channel=nightly&noStatistic=false
  override fun downloadUrl(): String {
    return buildString {
      append("https://plugins.jetbrains.com/plugin/download")
      append("?pluginId=${pluginIdEscaped()}")
      append("&noStatistic=true")
      append("&version=$version")
      channel?.let {
        append("&channel=$it")
      }
    }
  }
}