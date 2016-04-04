/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.settingsRepository

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.ui.UIUtil
import org.jetbrains.keychain.Credentials

fun showAuthenticationForm(credentials: Credentials?, uri: String, host: String?, path: String?, sshKeyFile: String?): Credentials? {
  if (ApplicationManager.getApplication()?.isUnitTestMode === true) {
    throw AssertionError("showAuthenticationForm called from tests")
  }

  return UIUtil.invokeAndWaitIfNeeded(object : Computable<Credentials?> {
    override fun compute(): Credentials? {
      val note = if (sshKeyFile == null) icsMessage(if (host == "github.com") "login.github.note" else "login.other.git.provider.note") else null
      var username = credentials?.id
      if (username == null && host == "github.com" && path != null && sshKeyFile == null) {
        val firstSlashIndex = path.indexOf('/', 1)
        username = path.substring(1, if (firstSlashIndex == -1) path.length else firstSlashIndex)
      }

      val authenticationForm = RepositoryAuthenticationForm(if (sshKeyFile == null) {
        icsMessage("log.in.to", StringUtil.trimMiddle(uri, 50))
      }
      else {
        icsMessage("enter.your.password.for.ssh.key", PathUtilRt.getFileName(sshKeyFile))
      }, username, credentials?.token, note, sshKeyFile != null)
      if (authenticationForm.showAndGet()) {
        username = sshKeyFile ?: authenticationForm.username
        val passwordChars = authenticationForm.password
        return Credentials(username, if (passwordChars == null) (if (username == null) null else "x-oauth-basic") else String(passwordChars))
      }
      else {
        return null
      }
    }
  })
}