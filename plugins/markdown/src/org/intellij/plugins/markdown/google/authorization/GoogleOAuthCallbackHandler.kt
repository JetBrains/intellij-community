// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.authorization

import com.intellij.collaboration.auth.OAuthCallbackHandlerBase
import com.intellij.collaboration.auth.services.OAuthService
import com.intellij.openapi.components.service
import com.intellij.util.Urls
import org.jetbrains.ide.BuiltInServerManager

class GoogleOAuthCallbackHandler : OAuthCallbackHandlerBase() {
  override fun oauthService(): OAuthService<*> = service<GoogleOAuthService>()

  override fun handleAcceptCode(isAccepted: Boolean): AcceptCodeHandleResult {
    val port = BuiltInServerManager.getInstance().port
    //TODO: Maybe fix them to something like https://account.jetbrains.com/[SERVICE NAME]/intellij/[...] or create these pages somewhere? 🤔
    // note: so far, these addresses are 404
    val redirectUrl = if (isAccepted) {
      Urls.newFromEncoded("http://localhost:$port/success")
    }
    else {
      Urls.newFromEncoded("http://localhost:$port/error")
    }
    return AcceptCodeHandleResult.Redirect(redirectUrl)
  }
}
