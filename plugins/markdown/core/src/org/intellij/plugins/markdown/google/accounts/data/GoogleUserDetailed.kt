// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.accounts.data

import com.intellij.collaboration.auth.AccountDetails

data class GoogleUserDetailed(
  override val name: String,
  val id: String,
  val givenName: String,
  val familyName: String,
  val locale: String
) : AccountDetails
