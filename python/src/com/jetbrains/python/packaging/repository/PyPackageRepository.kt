// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.repository

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.Transient
import com.jetbrains.python.packaging.cache.PythonSimpleRepositoryCache
import com.jetbrains.python.packaging.common.PythonPackageSpecification
import com.jetbrains.python.packaging.common.PythonSimplePackageSpecification
import com.jetbrains.python.packaging.conda.CondaPackageRepository
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import org.apache.http.client.utils.URIBuilder
import org.jetbrains.annotations.ApiStatus
import java.net.URL

@ApiStatus.Internal
open class PyPackageRepository() {

  var name: String = ""
    internal set
  var repositoryUrl: String? = null
    internal set
  var login: String? = null
    internal set
  var authorizationType: PyPackageRepositoryAuthenticationType = PyPackageRepositoryAuthenticationType.NONE
    internal set

  constructor(name: String, repositoryUrl: String?, login: String?) : this() {
    this.name = name
    this.repositoryUrl = repositoryUrl
    this.login = login
  }

  internal val isCustom: Boolean
   get() = this !is PyPIPackageRepository && this !is CondaPackageRepository

  private val serviceName: String
    get() = generateServiceName(SUBSYSTEM_NAME, name)

  val urlForInstallation: URL
    get() = repositoryUrl?.let { baseUrl ->
      val userLogin = login.takeUnless { it.isNullOrBlank() } ?: return URL(baseUrl)
      val userPassword = getPassword() ?: return URL(baseUrl)
      buildAuthenticatedUrl(baseUrl, userLogin, userPassword)
    } ?: URL("")

  private fun buildAuthenticatedUrl(baseUrl: String, login: String, password: String): URL =
    URIBuilder(baseUrl).setUserInfo(login, password).build().toURL()

  @Transient
  fun getPassword(): String? {
    val attributes = CredentialAttributes(serviceName, login)
    return PasswordSafe.instance.getPassword(attributes)
  }

  fun setPassword(pass: String?) {
    val attributes = CredentialAttributes(serviceName, login)
    PasswordSafe.instance.set(attributes, Credentials(login, pass))
  }

  fun clearCredentials() {
    val attributes = CredentialAttributes(serviceName, login)
    PasswordSafe.instance.set(attributes, null)
  }

  open fun createPackageSpecification(
    packageName: String,
    version: String? = null,
    relation: PyRequirementRelation? = null,
  ): PythonPackageSpecification =
    PythonSimplePackageSpecification(packageName, version, this, relation)

  open fun createForcedSpecPackageSpecification(
    packageName: String,
    versionSpecs: String? = null,
  ): PythonPackageSpecification =
    PythonSimplePackageSpecification(packageName, null, this).apply {
      this.versionSpecs = versionSpecs
    }

  open fun getPackages(): Set<String> =
    service<PythonSimpleRepositoryCache>()[this] ?: emptySet()

  companion object {
    private const val SUBSYSTEM_NAME = "PyCharm"
  }
}