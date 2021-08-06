// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.google.accounts.GoogleAccountManager
import org.intellij.plugins.markdown.google.accounts.GoogleAccountsListModel
import org.intellij.plugins.markdown.google.utils.GoogleAccountsUtils.createGoogleAccountPanel

internal class GoogleSettingsConfigurable internal constructor(private val project: Project)
  : BoundConfigurable(MarkdownBundle.message("markdown.google.accounts.preferences")) {

  private val accountsListModel = GoogleAccountsListModel(project)
  private val accountManager = service<GoogleAccountManager>()

  override fun createPanel(): DialogPanel = createGoogleAccountPanel(project, disposable!!, accountsListModel, accountManager)
}
