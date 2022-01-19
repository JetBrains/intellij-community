package com.intellij.settingsSync

import com.intellij.openapi.util.NlsSafe

internal sealed class SettingsSyncPushResult {
  object Success : SettingsSyncPushResult() {
    override fun toString(): String = "SUCCESS"
  }

  object Rejected: SettingsSyncPushResult() {
    override fun toString(): String = "REJECTED"
  }

  class Error(@NlsSafe val message: String): SettingsSyncPushResult() {
    override fun toString(): String = "ERROR[$message]"
  }
}

