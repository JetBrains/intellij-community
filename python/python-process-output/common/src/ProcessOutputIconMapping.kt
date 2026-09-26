package com.intellij.python.processOutput.common

import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
@JvmInline
value class ProcessBinaryFileName(val name: String)

@ApiStatus.Internal
data class ProcessIcon(val icon: Icon, val iconClass: Class<*>)

@ApiStatus.Internal
data class ProcessMatcher(val matcher: (ProcessBinaryFileName) -> Boolean, val icon: ProcessIcon)

@ApiStatus.Internal
abstract class ProcessOutputIconMapping {
  open val mapping: Map<ProcessBinaryFileName, ProcessIcon> = mapOf()
  open val matchers: List<ProcessMatcher> = listOf()
}