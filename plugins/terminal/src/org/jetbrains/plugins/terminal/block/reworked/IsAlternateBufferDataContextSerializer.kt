// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked

import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.DataKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer

internal class IsAlternateBufferDataContextSerializer : CustomDataContextSerializer<Boolean> {
  override val key: DataKey<Boolean>
    get() = IS_ALTERNATE_BUFFER_KEY
  override val serializer: KSerializer<Boolean>
    get() = Boolean.serializer()
}
