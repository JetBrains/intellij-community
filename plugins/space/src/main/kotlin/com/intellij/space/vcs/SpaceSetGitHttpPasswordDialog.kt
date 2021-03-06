// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs

import circlet.client.api.TD_MemberProfile
import circlet.client.api.identifier
import circlet.client.api.impl.vcsPasswords
import circlet.client.repoService
import circlet.platform.client.KCircletClient
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.space.messages.SpaceBundle
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.layout.*
import com.intellij.util.UriUtil
import com.intellij.util.io.URLUtil
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import git4idea.commands.GitHttpAuthenticator
import libraries.coroutines.extra.LifetimeSource
import libraries.coroutines.extra.launch
import libraries.coroutines.extra.usingSource
import libraries.klogging.logger
import runtime.RpcException
import runtime.Ui
import runtime.message
import java.util.concurrent.CancellationException
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class SpaceSetGitHttpPasswordDialog(
  private val me: TD_MemberProfile,
  private val client: KCircletClient
) : DialogWrapper(null, false) {
  private val log = logger<SpaceSetGitHttpPasswordDialog>()

  private val lifetime: LifetimeSource = LifetimeSource()

  internal var result: SpaceHttpPasswordState = SpaceHttpPasswordState.NotSet

  private val passwordSafe: PasswordSafe = PasswordSafe.instance

  private val passwordField: JBPasswordField = JBPasswordField()
  private val rememberPassword: JCheckBox = JCheckBox(UIBundle.message("auth.remember.cb"), passwordSafe.isRememberPasswordByDefault)

  private val asyncProcessIcon = AsyncProcessIcon("Set password").apply {
    isVisible = false
  }

  init {
    title = SpaceBundle.message("set.http.password.dialog.title")
    setOKButtonText(SpaceBundle.message("set.http.password.dialog.ok.button"))
    init()
    Disposer.register(disposable, Disposable { lifetime.terminate() })
  }

  override fun doOKAction() {
    launch(lifetime, Ui) {
      okAction.isEnabled = false
      asyncProcessIcon.isVisible = true

      lifetime.usingSource {
        try {
          val password = passwordField.password
          log.info("Trying to set HTTP Git password")
          client.api.vcsPasswords().setVcsPassword(me.identifier, String(password))
          val httpPassword = client.api.vcsPasswords().getVcsPassword(me.identifier)
          log.info("Password set")

          result = if (httpPassword == null) SpaceHttpPasswordState.NotSet else SpaceHttpPasswordState.Set(httpPassword)

          if (rememberPassword.isSelected) {
            val username = me.username
            val repoService = client.repoService
            val httpUrl = repoService.getRepoUrlPatterns().httpUrl

            if (httpUrl != null) {
              val key = makeKey(getGitUrlHost(httpUrl), username)
              val credentialAttributes = CredentialAttributes(generateServiceName("Git HTTP", key), key, GitHttpAuthenticator::class.java)
              val credentials = Credentials(username, password)
              passwordSafe.set(credentialAttributes, credentials)
            }
          }

          close(OK_EXIT_CODE)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: RpcException) {
          log.error(e, e.failure.message())
          setErrorText(e.failure.message()) // NON-NLS
        }
        catch (e: Exception) {
          log.error(e, "Unable to set password")
          setErrorText(SpaceBundle.message("set.http.password.dialog.error.text.unable.to.set.password"))
        }
      }

      asyncProcessIcon.isVisible = false
      okAction.isEnabled = true
    }
  }

  override fun createCenterPanel(): JComponent = panel {
    row(SpaceBundle.message("set.http.password.dialog.username.label")) {
      JBTextField(me.username)().component.apply { // NON-NLS
        isEditable = false
      }
    }
    row(UIBundle.message("auth.password.label")) {
      passwordField().focused()
    }
    row {
      rememberPassword()
    }
  }

  override fun doValidate(): ValidationInfo? {
    if (passwordField.password.isEmpty()) {
      return ValidationInfo(SpaceBundle.message("set.http.password.dialog.validation.message.empty.password"), passwordField)
    }
    return null
  }

  override fun createSouthPanel(): JComponent {
    val buttons = super.createSouthPanel()
    return JPanel(HorizontalLayout(8, SwingConstants.BOTTOM)).apply {
      asyncProcessIcon.border = buttons.border
      add(asyncProcessIcon, HorizontalLayout.RIGHT)
      add(buttons, HorizontalLayout.RIGHT)
    }
  }
}

fun makeKey(url: String, login: String): String {
  val pair = UriUtil.splitScheme(url)
  val scheme: String = pair.getFirst()
  return if (!StringUtil.isEmpty(scheme)) {
    scheme + URLUtil.SCHEME_SEPARATOR + login + "@" + pair.getSecond()
  }
  else "$login@$url"
}

fun getGitUrlHost(url: String): String {
  val host = url.substringAfter("://").substringBefore("/")
  return "http://$host"
}
