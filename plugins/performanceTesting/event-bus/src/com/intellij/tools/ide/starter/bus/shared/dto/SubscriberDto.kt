// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.starter.bus.shared.dto

data class SubscriberDto(val subscriberName: String,
                         val eventName: String,
                         val processId: String,
                         val timeoutMs: Long = 0L)