package com.intellij.terminal.backend

import com.intellij.util.xmlb.annotations.XCollection

internal data class TerminalSessionPersistedTab(
  val name: String?,
  @XCollection()
  val shellCommand: List<String>?,
) {
  @Suppress("unused")  // It is used in deserialization
  constructor() : this(null, null)
}