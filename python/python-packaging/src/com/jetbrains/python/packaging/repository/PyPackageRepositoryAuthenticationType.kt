// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.repository

import com.jetbrains.python.packaging.PyPackagingBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.PropertyKey

@ApiStatus.Experimental
enum class PyPackageRepositoryAuthenticationType(
  private val textKey: @PropertyKey(resourceBundle = "messages.PyPackagingBundle") String,
) {
  NONE("python.packaging.repository.form.authorization.none"),
  HTTP("python.packaging.repository.form.authorization.basic");

  val text: String
    get() = PyPackagingBundle.message(textKey)

  /** `true` iff the repository form should show login + password fields for this auth kind. */
  val requiresCredentials: Boolean
    get() = when (this) {
      NONE -> false
      HTTP -> true
    }
}

