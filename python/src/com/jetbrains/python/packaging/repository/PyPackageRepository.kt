// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.repository

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.service
import com.intellij.util.io.HttpRequests
import com.intellij.util.xmlb.annotations.Transient
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPIPackageUtil
import com.jetbrains.python.packaging.PyPackageVersionComparator
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.cache.PythonSimpleRepositoryCache
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.common.PythonSimplePackageDetails
import org.apache.http.client.utils.URIBuilder
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.net.URL

private val GSON = Gson()

internal fun PyPackageRepository.buildPackageDetailsBySimpleDetailsProtocol(packageName: String): PyResult<PythonSimplePackageDetails> {
  val repositoryUrl = repositoryUrl ?: return PyResult.failure(MessageError("There is no repository url for $name"))

  val packageDetails = runCatching {
    val packageDetailsUrl = PyPIPackageUtil.buildDetailsUrl(repositoryUrl, packageName)
    val rawInfo = HttpRequests.request(packageDetailsUrl)
      .withBasicAuthorization(this)
      .readTimeout(3000)
      .readString()

    GSON.fromJson(rawInfo, PyPIPackageUtil.PackageDetails::class.java)
  }.getOrElse { throwable ->
    when (throwable) {
      is JsonSyntaxException, is PyPIPackageUtil.NotSimpleRepositoryApiUrlException, is IOException -> return PyResult.localizedError(throwable.localizedMessage)
      else -> throw throwable
    }
  }

  val pythonSimplePackageDetails = PythonSimplePackageDetails(
    name = packageName,
    availableVersions = packageDetails.releases.sortedWith(PyPackageVersionComparator.STR_COMPARATOR.reversed()),
    repository = this,
    summary = packageDetails.info.summary,
    description = packageDetails.info.description,
    descriptionContentType = packageDetails.info.descriptionContentType,
    documentationUrl = packageDetails.info.projectUrls["Documentation"],
    author = packageDetails.info.author,
    authorEmail = packageDetails.info.authorEmail,
    homepageUrl = packageDetails.info.homePage
  )
  return PyResult.success(pythonSimplePackageDetails)
}

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

  private val cachedPassword = CachedPassword()

  val urlForInstallation: URL?
    get() {
      val baseUrl = repositoryUrl ?: return null
      val userLogin = login.takeUnless { it.isNullOrBlank() } ?: return URL(baseUrl)
      val userPassword = getPassword() ?: return URL(baseUrl)
      return buildAuthenticatedUrl(baseUrl, userLogin, userPassword)
    }

  private fun buildAuthenticatedUrl(baseUrl: String, login: String, password: String): URL =
    URIBuilder(baseUrl).setUserInfo(login, password).build().toURL()

  @Transient
  fun getPassword(): String? = cachedPassword.get()

  fun setPassword(pass: String?) = cachedPassword.set(Credentials(login, pass))

  fun clearCredentials() = cachedPassword.set(null)

  @ApiStatus.Internal
  fun findPackageSpecificationWithSpec(pyRequirement: PyRequirement): PythonRepositoryPackageSpecification? =
    if (hasPackage(pyRequirement))
      PythonRepositoryPackageSpecification(this, pyRequirement)
    else
      null

  @ApiStatus.Internal
  fun findPackageSpecification(
    pyRequirement: PyRequirement,
  ): PythonRepositoryPackageSpecification? {
    return findPackageSpecificationWithSpec(pyRequirement)
  }


  protected open fun hasPackage(pyPackage: PyRequirement): Boolean = pyPackage.name in getPackages()

  open fun getPackages(): Set<String> = service<PythonSimpleRepositoryCache>()[this] ?: emptySet()

  open fun buildPackageDetails(packageName: String): PyResult<PythonPackageDetails> {
    return buildPackageDetailsBySimpleDetailsProtocol(packageName)
  }

  companion object {
    private const val SUBSYSTEM_NAME = "PyCharm"
  }

  /**
   * Thread-safe cached wrapper around [PasswordSafe] for a single credential.
   *
   * Caches the password on first [get] to prevent concurrent blocking calls to [PasswordSafe]
   * from exhausting the thread pool (see PY-87597). In remote development, [PasswordSafe.getPassword]
   * goes through `RemoteCredentialStore` which calls `runBlockingMaybeCancellable`, blocking the calling thread.
   */
  private inner class CachedPassword {
    @Volatile
    private var cached: CachedValue? = null

    private fun credentialAttributes() = CredentialAttributes(generateServiceName(SUBSYSTEM_NAME, name), login)

    fun get(): String? {
      cached?.let { return it.value }
      synchronized(this) {
        cached?.let { return it.value }
        val password = PasswordSafe.instance.getPassword(credentialAttributes())
        cached = CachedValue(password)
        return password
      }
    }

    fun set(credentials: Credentials?) {
      cached = null
      PasswordSafe.instance[credentialAttributes()] = credentials
    }
  }

  /**
   * Wrapper to distinguish "not yet cached" (`null` reference) from "cached null password" ([CachedValue] with `null` inside).
   */
  @JvmInline
  private value class CachedValue(val value: String?)
}
