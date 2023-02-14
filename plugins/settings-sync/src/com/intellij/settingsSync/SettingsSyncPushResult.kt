package com.intellij.settingsSync

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed class SettingsSyncPushResult {
  class Success(val serverVersionId: String?) : SettingsSyncPushResult() {
    override fun toString(): String = "SUCCESS"
  }

  object Rejected: SettingsSyncPushResult() {
    override fun toString(): String = "REJECTED"
  }

  class Error(@NlsSafe val message: String): SettingsSyncPushResult() {
    override fun toString(): String = "ERROR[$message]"
  }
}

