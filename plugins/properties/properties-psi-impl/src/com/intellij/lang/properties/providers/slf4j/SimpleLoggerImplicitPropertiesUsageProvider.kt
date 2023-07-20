// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.providers.slf4j

import com.intellij.lang.properties.codeInspection.unused.ImplicitPropertyUsageProvider
import com.intellij.lang.properties.psi.Property

internal class SimpleLoggerImplicitPropertiesUsageProvider : ImplicitPropertyUsageProvider {
  override fun isUsed(property: Property): Boolean {
    val file = property.containingFile
    if (file?.name != SIMPLE_LOGGER_PROPERTIES_CONFIG) return false

    val key = property.key ?: return false

    return SIMPLE_LOGGER_PROPERTIES.contains(key)
           || key.startsWith(SIMPLE_LOGGER_LOG_PREFIX)
  }
}