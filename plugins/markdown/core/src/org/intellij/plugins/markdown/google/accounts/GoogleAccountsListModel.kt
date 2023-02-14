// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.accounts

import com.intellij.collaboration.auth.ui.MutableAccountsListModel
import org.intellij.plugins.markdown.google.accounts.data.GoogleAccount
import org.intellij.plugins.markdown.google.authorization.GoogleCredentials

class GoogleAccountsListModel : MutableAccountsListModel<GoogleAccount, GoogleCredentials>()
