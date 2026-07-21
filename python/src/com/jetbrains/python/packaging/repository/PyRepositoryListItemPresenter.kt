// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.repository

internal data class PyRepositoryFormViewState(
  val nameError: Boolean,
  val urlError: Boolean,
)

internal class PyRepositoryListItemPresenter(
  private val isDefault: Boolean,
  private val getAllNames: () -> List<String>,
) {
  fun computeViewState(name: String, url: String): PyRepositoryFormViewState = PyRepositoryFormViewState(
    nameError = !isDefault && isDuplicateName(name),
    urlError = !isDefault && url.isNotBlank() && !isValidRepositoryUrl(url),
  )

  fun hasErrors(name: String, url: String): Boolean {
    val (nameError, urlError) = computeViewState(name, url)
    return nameError || urlError
  }

  private fun isDuplicateName(name: String): Boolean =
    name.isNotBlank() && getAllNames().count { it == name } > 1
}
