// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.accounts.data

import com.intellij.collaboration.auth.Account
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import org.jetbrains.annotations.VisibleForTesting

@Tag("account")
data class GoogleAccount(
  @Attribute("id")
  @VisibleForTesting
  override val id: String = "",

  @Attribute("name")
  @NlsSafe
  override var name: String = ""
) : Account() {

  override fun toString(): String = name
}
