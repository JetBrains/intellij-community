// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import org.jetbrains.annotations.ApiStatus
import java.net.URI

@ApiStatus.Internal
sealed class PythonPackageInstallRequest(val title: @NlsSafe String) {
  data class ByLocation(val location: URI) : PythonPackageInstallRequest(location.toString())
  data class ByRepositoryPythonPackageSpecifications(val specifications: List<PythonRepositoryPackageSpecification>) : PythonPackageInstallRequest(
    specifications.joinToString(", ") { it.nameWithVersionSpec })
}

@ApiStatus.Internal
fun PythonRepositoryPackageSpecification.toInstallRequest(): PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications {
  return PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(listOf(this))
}