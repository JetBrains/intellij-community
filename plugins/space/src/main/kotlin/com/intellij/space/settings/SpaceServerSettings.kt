package com.intellij.space.settings

data class SpaceServerSettings(
  var enabled: Boolean = false,
  var server: String = ""
)
