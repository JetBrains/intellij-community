// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.bus

import com.intellij.tools.ide.starter.bus.events.Event
import kotlin.time.Duration

data class Subscriber<T : Event>(val subscriberName: Any,
                                 val timeout: Duration,
                                 val callback: suspend (event: T) -> Unit)