// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.utils

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.intellij.openapi.project.Project
import org.intellij.plugins.markdown.google.authorization.GoogleCredentials
import org.intellij.plugins.markdown.google.utils.GoogleAccountsUtils

object GoogleCommonUtils {
  fun <T: Any?> runProcessWithGoogleCredentials(project: Project, credential: Credential, process: (Credential) -> T): T? {
    try {
      return process(credential)
    }
    catch (e: GoogleJsonResponseException) {
      if (e.statusCode == 401) {
        val accountCredentials: GoogleCredentials = GoogleAccountsUtils.tryToReLogin(project) ?: return null
        val newCredential: Credential = GoogleAccountsUtils.createCredentialsForGoogleApi(accountCredentials)

        return runProcessWithGoogleCredentials(project, newCredential, process)
      } else throw e
    }
  }
}
