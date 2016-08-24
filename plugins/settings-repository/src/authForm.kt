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

import com.intellij.credentialStore.Credentials
import com.intellij.layout.*
import com.intellij.layout.CCFlags.*
import com.intellij.layout.LCFlags.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.dialog
import com.intellij.openapi.util.Computable
import com.intellij.ui.DocumentAdapter
import com.intellij.util.PathUtilRt
import com.intellij.util.nullize
import com.intellij.util.trimMiddle
import com.intellij.util.ui.UIUtil
import javax.swing.JPasswordField
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

fun showAuthenticationForm(credentials: Credentials?, uri: String, host: String?, path: String?, sshKeyFile: String?): Credentials? {
  if (ApplicationManager.getApplication()?.isUnitTestMode === true) {
    throw AssertionError("showAuthenticationForm called from tests")
  }

  val isGitHub = host == "github.com"
    val note = if (sshKeyFile == null) icsMessage(if (isGitHub) "login.github.note" else "login.other.git.provider.note") else null
    var username = credentials?.userName
    if (username == null && isGitHub && path != null && sshKeyFile == null) {
      val firstSlashIndex = path.indexOf('/', 1)
      username = path.substring(1, if (firstSlashIndex == -1) path.length else firstSlashIndex)
    }

    val message = if (sshKeyFile == null) icsMessage("log.in.to", uri.trimMiddle(50)) else icsMessage("enter.your.password.for.ssh.key", PathUtilRt.getFileName(sshKeyFile))

  return UIUtil.invokeAndWaitIfNeeded(Computable {
    val userField = JTextField(username)
    val passwordField = JPasswordField(credentials?.password)

    val centerPanel = panel(fillX) {
      label(message, wrap, span, bold = true, gapBottom = 10)

      if (sshKeyFile == null && !isGitHub) {
        label("Username:")
        userField(grow, wrap)
      }

      label(if (sshKeyFile == null && isGitHub) "Token:" else "Password:")
      passwordField(grow, wrap)

      note?.let { noteComponent(it)(skip) }
    }

    val authenticationForm = dialog(
        title = "Settings Repository",
        resizable = false,
        centerPanel = centerPanel,
        preferedFocusComponent = userField,
        okActionEnabled = false)

    passwordField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        authenticationForm.okActionEnabled(e.document.length != 0)
      }
    })
    authenticationForm.okActionEnabled(false)

    if (authenticationForm.showAndGet()) {
      username = sshKeyFile ?: userField.text.nullize(true)
      val passwordChars = passwordField.password
      Credentials(username, if (passwordChars == null || passwordChars.isEmpty()) (if (username == null) null else "x-oauth-basic") else String(passwordChars))
    }
    else {
      null
    }
  })
}