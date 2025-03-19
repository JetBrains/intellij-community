package com.intellij.tools.ide.starter.bus.shared.dto

data class SubscriberDto(val subscriberName: String,
                         val eventName: String,
                         val processId: String,
                         val timeoutMs: Long = 0L)