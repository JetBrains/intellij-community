// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.repository

import com.jetbrains.python.PyBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
enum class PyPackageRepositoryAuthenticationType(private val textKey: String) {
  NONE("python.packaging.repository.form.authorization.none"),
  HTTP("python.packaging.repository.form.authorization.basic");

  val text: String
    get() = PyBundle.message(textKey)
}

