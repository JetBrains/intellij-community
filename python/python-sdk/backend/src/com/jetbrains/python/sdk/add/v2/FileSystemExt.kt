package com.jetbrains.python.sdk.add.v2

import com.intellij.python.pytools.ToolCommandSpec
import com.jetbrains.python.PyInternalExecApi
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@PyInternalExecApi
suspend fun <P : PathHolder> FileSystem<P>.detectTool(toolName: String, filter: (P) -> Boolean = { true }): P? =
  detectTool(ToolCommandSpec(toolName, emptyList()), filter)