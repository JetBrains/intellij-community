// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import org.jetbrains.annotations.ApiStatus

/** The names of the packages that make up the package management of an environment. */
@ApiStatus.Internal
object PyPackageNames {
  const val SETUPTOOLS: String = "setuptools"
  const val PIP: String = "pip"
  const val DISTRIBUTE: String = "distribute"
}