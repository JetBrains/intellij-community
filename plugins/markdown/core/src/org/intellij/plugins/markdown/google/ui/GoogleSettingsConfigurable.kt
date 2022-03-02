// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.registry.Registry
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.google.accounts.GoogleAccountManager
import org.intellij.plugins.markdown.google.accounts.GoogleAccountsListModel
import org.intellij.plugins.markdown.google.utils.GoogleAccountsUtils.createGoogleAccountPanel

internal class GoogleSettingsConfigurableProvider : ConfigurableProvider() {

  override fun createConfigurable(): Configurable = GoogleSettingsConfigurable()

  override fun canCreateConfigurable(): Boolean = Registry.`is`("markdown.google.docs.import.action.enable")
}

internal class GoogleSettingsConfigurable : BoundConfigurable(MarkdownBundle.message("markdown.google.accounts.preferences")) {

  private val accountsListModel = GoogleAccountsListModel()
  private val accountManager = service<GoogleAccountManager>()

  override fun createPanel(): DialogPanel = createGoogleAccountPanel(disposable!!, accountsListModel, accountManager)
}
