// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.black.statistics

import com.intellij.notification.impl.NotificationIdsHolder


class BlackFormatterIntegrationIdsHolder : NotificationIdsHolder {
  override fun getNotificationIds(): List<String> = listOf(
    BLACK_FORMATTER_SUPPORT,
    BLACK_FORMATTER_TIMEOUT,
    BLACK_FORMATTER_SDK_NOT_CONFIGURED,
    BLACK_FORMATTER_FAILED,
    BLACK_FORMATTER_EXCEPTION,
  )

  companion object {
    const val BLACK_FORMATTER_SUPPORT = "black.formatter.support"
    const val BLACK_FORMATTER_TIMEOUT = "black.formatter.timeout"
    const val BLACK_FORMATTER_SDK_NOT_CONFIGURED = "black.formatter.sdk.not.configured"
    const val BLACK_FORMATTER_FAILED = "black.formatter.failed"
    const val BLACK_FORMATTER_EXCEPTION = "black.formatter.exception"
  }
}
