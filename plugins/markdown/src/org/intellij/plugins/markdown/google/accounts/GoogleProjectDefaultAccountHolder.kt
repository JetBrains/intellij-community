// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.accounts

import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.auth.PersistentDefaultAccountHolder
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownNotifier
import org.intellij.plugins.markdown.google.accounts.data.GoogleAccount

@State(name = "GoogleDefaultAccount", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)], reportStatistic = false)
class GoogleProjectDefaultAccountHolder(project: Project) : PersistentDefaultAccountHolder<GoogleAccount>(project) {
  override fun accountManager(): AccountManager<GoogleAccount, *> = service<GoogleAccountManager>()

  override fun notifyDefaultAccountMissing() = runInEdt {
    MarkdownNotifier.showErrorNotification(project, MarkdownBundle.message("markdown.google.accounts.default.missing"))
  }
}
