// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.reworked.hyperlinks

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun isSplitHyperlinksSupportEnabled(): Boolean = Registry.`is`("terminal.split.hyperlinks", defaultValue = false)
