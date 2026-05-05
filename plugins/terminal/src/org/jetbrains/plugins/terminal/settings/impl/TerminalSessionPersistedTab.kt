package org.jetbrains.plugins.terminal.settings.impl

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.startup.TerminalProcessType

@ApiStatus.Internal
data class TerminalSessionPersistedTab(
  @Attribute
  val name: String?,
  @Attribute
  val isUserDefinedName: Boolean,
  @XCollection
  val shellCommand: List<String>?,
  @Attribute
  val workingDirectory: String?,
  @XCollection
  val envVariables: Map<String, String>?,
  @Attribute
  val processType: TerminalProcessType?,
) {
  @Suppress("unused")  // It is used in deserialization
  constructor() : this(null, false, null, null, null, null)
}