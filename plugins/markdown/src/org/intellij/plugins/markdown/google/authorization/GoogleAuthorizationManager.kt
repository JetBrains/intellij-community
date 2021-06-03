// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.google.authorization

import com.google.api.client.auth.oauth2.Credential
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.google.utils.GoogleCredentialUtils.createCredentialsForGoogleApi
import org.intellij.plugins.markdown.google.utils.GoogleCredentialUtils.getGoogleAppCredentials

class GoogleAuthorizationManager(private val project: Project) {
  fun getCredentials(): Credential? {
    val credentials = createNewAccount() ?: return null

    return createCredentialsForGoogleApi(credentials)
  }

  private fun createNewAccount(): GoogleCredentials? {
    val oAuthService = service<GoogleOAuthService>().apply {
      googleAppCred = getGoogleAppCredentials(project) ?: return null
    }
    val credentialsFuture = oAuthService.authorize()

    try {
      return ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
        ProgressIndicatorUtils.awaitWithCheckCanceled(credentialsFuture)
      }, MarkdownBundle.message("markdown.google.account.login.progress.title"), true, project)
    }
    catch (e: ProcessCanceledException) {
      credentialsFuture.cancel(true)
      return null
    }
  }
}
