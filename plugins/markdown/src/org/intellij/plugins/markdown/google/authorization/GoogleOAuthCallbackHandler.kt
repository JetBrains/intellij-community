// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.authorization

import com.intellij.collaboration.auth.OAuthCallbackHandlerBase
import com.intellij.openapi.components.service
import com.intellij.collaboration.auth.services.OAuthService

class GoogleOAuthCallbackHandler : OAuthCallbackHandlerBase() {
  override fun oauthService(): OAuthService<*> = service<GoogleOAuthService>()
}
