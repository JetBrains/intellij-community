// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.black.common

import com.intellij.python.pytools.common.PyToolConfigurationDto
import com.intellij.python.pytools.common.PyToolConfigurationSerializerProvider
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable
@SerialName("python.black")
data class PyBlackToolConfigurationDto(val arguments: String) : PyToolConfigurationDto

class PyBlackToolConfigurationSerializerProvider :
  PyToolConfigurationSerializerProvider<PyBlackToolConfigurationDto> {
  override val configurationClass: KClass<PyBlackToolConfigurationDto> = PyBlackToolConfigurationDto::class
  override val serializer: KSerializer<PyBlackToolConfigurationDto> = PyBlackToolConfigurationDto.serializer()
}
