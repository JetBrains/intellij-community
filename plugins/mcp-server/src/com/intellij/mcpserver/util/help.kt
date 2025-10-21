package com.intellij.mcpserver.util

import com.intellij.openapi.application.ApplicationNamesInfo
import java.util.*

fun getHelpLink(topic: String): String {
  val helpIdeName: String = when (val name = ApplicationNamesInfo.getInstance().productName) {
    "GoLand" -> "go"
    "RubyMine" -> "ruby"
    "AppCode" -> "objc"
    else -> name.lowercase(Locale.ENGLISH)
  }
  return "https://www.jetbrains.com/help/$helpIdeName/$topic"
}