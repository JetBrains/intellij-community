package com.intellij.terminal.backend

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.XCollection

internal data class TerminalSessionPersistedTab(
  @Attribute
  val name: String?,
  @Attribute
  val isUserDefinedName: Boolean,
  @XCollection()
  val shellCommand: List<String>?,
  @Attribute
  val workingDirectory: String?,
) {
  @Suppress("unused")  // It is used in deserialization
  constructor() : this(null, false, null, null)
}