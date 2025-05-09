// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyPackageRepositoryUtil")

package com.jetbrains.python.packaging.repository

import com.intellij.openapi.components.service
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.RequestBuilder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.failure
import com.jetbrains.python.getOrNull
import com.jetbrains.python.packaging.PyPIPackageUtil
import com.jetbrains.python.packaging.PyPackageVersionComparator
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonSimplePackageDetails
import com.jetbrains.python.packaging.pip.PypiPackageCache
import kotlinx.io.IOException
import org.jetbrains.annotations.ApiStatus
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*


@ApiStatus.Experimental
internal fun RequestBuilder.withBasicAuthorization(repository: PyPackageRepository?): RequestBuilder {
  if (repository == null) return this
  val password = repository.getPassword()
  if (repository.login != null && password != null) {
    val credentials = Base64.getEncoder().encode("${repository.login}:${password}".toByteArray()).toString(StandardCharsets.UTF_8)
    this.tuner { connection -> connection.setRequestProperty("Authorization", "Basic $credentials") }
  }
  return this
}

@ApiStatus.Experimental
internal fun PyPackageRepository.checkValid(): Boolean {
  return HttpRequests
    .request(repositoryUrl!!)
    .withBasicAuthorization(this)
    .connectTimeout(3000)
    .throwStatusCodeException(false)
    .tryConnect() == 200
}

@ApiStatus.Experimental
internal fun encodeCredentialsForUrl(login: String, password: String): String {
  return "${URLEncoder.encode(login, StandardCharsets.UTF_8)}:${URLEncoder.encode(password, StandardCharsets.UTF_8)}"
}

@ApiStatus.Experimental
object PyPIPackageRepository : PyPackageRepository("PyPI", PyPIPackageUtil.PYPI_LIST_URL, null) {
  override fun getPackages(): Set<String> {
    return service<PypiPackageCache>().packages
  }

  override fun buildPackageDetails(packageName: String): PyResult<PythonPackageDetails> {
    super.buildPackageDetails(packageName).getOrNull()?.let {
      return PyResult.success(it)
    }

    val repositoryUrl = repositoryUrl ?: return failure(PyBundle.message("python.packaging.error.no.repository.url", name))

    val versions = runCatching {
      PyPIPackageUtil.parsePackageVersionsFromRepository(repositoryUrl, packageName)
    }.getOrElse { throwable ->
      when (throwable) {
        is PyPIPackageUtil.NotSimpleRepositoryApiUrlException, is IOException -> return failure(throwable.localizedMessage)
        else -> throw throwable
      }
    }

    val simplePackageDetails = PythonSimplePackageDetails(
      name = packageName,
      availableVersions = versions.sortedWith(PyPackageVersionComparator.STR_COMPARATOR.reversed()),
      repository = this,
      description = PyBundle.message("python.packages.no.details.in.repo", name)
    )
    return PyResult.success(simplePackageDetails)
  }
}