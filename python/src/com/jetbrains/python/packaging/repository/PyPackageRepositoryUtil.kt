// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyPackageRepositoryUtil")
package com.jetbrains.python.packaging.repository

import com.intellij.util.io.HttpRequests
import com.intellij.util.io.RequestBuilder
import org.jetbrains.annotations.ApiStatus
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*

@ApiStatus.Experimental
internal fun RequestBuilder.withBasicAuthorization(repository: PyPackageRepository): RequestBuilder {
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
object PyEmptyPackagePackageRepository : PyPackageRepository("empty repository", "", "")

@ApiStatus.Experimental
object PyPIPackageRepository : PyPackageRepository("PyPI", "https://pypi.python.org/simple", "")