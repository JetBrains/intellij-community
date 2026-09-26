// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core.common

import com.intellij.python.pytools.common.PyToolConfigurationDto
import com.intellij.python.pytools.common.PyToolConfigurationSerializerProvider
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
) : PyToolConfigurationDto

class PyLspToolConfigurationSerializerProvider :
  PyToolConfigurationSerializerProvider<PyLspToolConfigurationDto> {
  override val configurationClass: KClass<PyLspToolConfigurationDto> = PyLspToolConfigurationDto::class
  override val serializer: KSerializer<PyLspToolConfigurationDto> = PyLspToolConfigurationDto.serializer()
}
