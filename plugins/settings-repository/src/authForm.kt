// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository

import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.OneTimeString
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.panel
import com.intellij.util.PathUtilRt
import com.intellij.util.text.nullize
import com.intellij.util.text.trimMiddle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JPasswordField
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

internal suspend fun showAuthenticationForm(credentials: Credentials?,
                                            @NlsSafe uri: String,
                                            @NlsSafe host: String?,
                                            @NlsSafe path: String?,
                                            @NlsSafe sshKeyFile: String?): Credentials? {
  if (ApplicationManager.getApplication()?.isUnitTestMode == true) {
    throw AssertionError("showAuthenticationForm called from tests")
  }

  val isGitHub = host == "github.com"
  val isBitbucket = host == "bitbucket.org"
  val note = if (sshKeyFile == null) icsMessage(if (isGitHub) "login.github.note" else if (isBitbucket) "login.bitbucket.note" else "login.other.git.provider.note") else null
  var username = credentials?.userName
  if (username == null && isGitHub && path != null && sshKeyFile == null) {
    val firstSlashIndex = path.indexOf('/', 1)
    username = path.substring(1, if (firstSlashIndex == -1) path.length else firstSlashIndex)
  }

  val message = if (sshKeyFile == null)
    icsMessage("log.in.to", uri.trimMiddle(50))
  else IcsBundle.message("authentication.form.prompt.enter.ssh.key.password", PathUtilRt.getFileName(sshKeyFile))

  return withContext(Dispatchers.EDT) {
    val userField = JTextField(username)
    val passwordField = JPasswordField(credentials?.getPasswordAsString())

    val centerPanel = panel {
      noteRow(message)
      if (sshKeyFile == null && !isGitHub) {
        row(IcsBundle.message("authentication.form.username")) { userField() }
      }

      val authPrompt =
        if (sshKeyFile == null && isGitHub) IcsBundle.message("authentication.form.token")
        else IcsBundle.message("authentication.form.password")
      row(authPrompt) { passwordField() }

      note?.let { noteRow(it) }
    }

    val authenticationForm = dialog(
      title = IcsBundle.message("ics.settings"),
      panel = centerPanel,
      focusedComponent = if (userField.parent == null) passwordField else userField,
      okActionEnabled = false)

    passwordField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        authenticationForm.isOKActionEnabled = e.document.length != 0
      }
    })
    authenticationForm.isOKActionEnabled = false

    if (authenticationForm.showAndGet()) {
      username = sshKeyFile ?: userField.text.nullize(true)
      val passwordChars = passwordField.password.nullize()
      Credentials(username, if (passwordChars == null) (if (username == null) null else OneTimeString("x-oauth-basic")) else OneTimeString(passwordChars))
    }
    else {
      null
    }
  }
}