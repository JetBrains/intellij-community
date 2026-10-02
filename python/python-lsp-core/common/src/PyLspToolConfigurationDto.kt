// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core.common

import com.intellij.python.pytools.common.PyToolConfigurationDto
import com.intellij.python.pytools.common.PyToolConfigurationSerializerProvider
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KClass

@Serializable
@SerialName("python.lsp")
data class PyLspToolConfigurationDto(
  var inspections: Boolean,
  var completions: Boolean?,
  var inlayHints: Boolean?,
  var documentation: Boolean?,
  var formatting: Boolean? = null,
  var sortImports: Boolean? = null,
  var formatSortImports: Boolean? = null,
  var fixOnSave: Boolean? = null,
  /** The type checking mode, as the tool names it, or `null` when the tool offers no mode. */
  var typeCheckingMode: String? = null,
) : PyToolConfigurationDto

@ApiStatus.Internal
class PyLspToolConfigurationSerializerProvider :
  PyToolConfigurationSerializerProvider<PyLspToolConfigurationDto> {
  override val configurationClass: KClass<PyLspToolConfigurationDto> = PyLspToolConfigurationDto::class
  override val serializer: KSerializer<PyLspToolConfigurationDto> = PyLspToolConfigurationDto.serializer()
}
