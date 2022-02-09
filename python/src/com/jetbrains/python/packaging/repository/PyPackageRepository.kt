// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.repository

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.Transient
import org.jetbrains.annotations.ApiStatus
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@ApiStatus.Experimental
open class PyPackageRepository() : BaseState() {
  var name by string("")
  var repositoryUrl by string("")
  var authorizationType by enum(PyPackageRepositoryAuthenticationType.NONE)
  var login by string("")

  val urlForInstallation: String
    get() {
      val fullUrl = repositoryUrl!!
      if (login != null && login!!.isNotBlank()) {
        val password = getPassword()
        if (password != null) {
          val protocol = fullUrl.substringBefore("//")
          val url = fullUrl.substringAfter("//")
          return "$protocol//${encodeCredentialsForUrl(login!!, password)}@$url"
        }
      }
      return fullUrl
    }


  @Transient
  fun getPassword(): String? {
    val serviceName = generateServiceName("PyCharm", name!!)
    val attributes = CredentialAttributes(serviceName, login)
    return PasswordSafe.instance.getPassword(attributes)
  }

  fun setPassword(pass: String?) {
    val serviceName = generateServiceName("PyCharm", name!!)
    val attributes = CredentialAttributes(serviceName, login)
    PasswordSafe.instance.set(attributes, Credentials(login, pass))
  }

  fun clearCredentials() {
    val serviceName = generateServiceName("PyCharm", name!!)
    val attributes = CredentialAttributes(serviceName, login)
    PasswordSafe.instance.set(attributes, null)
  }

  constructor(name: String, repositoryUrl: String, username: String) : this() {
    this.name = name
    this.repositoryUrl = repositoryUrl
    this.login = username
  }
}