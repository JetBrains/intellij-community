// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.PropertyKey

@ApiStatus.Internal

enum class PyRequirementsVersionSpecifierType(
  val separator: String,
  val message: @PropertyKey(resourceBundle = "messages.PyPackagingBundle") String,
) {
  NO_VERSION("", "python.requirements.version.separator.no.version") {
    override fun toString() = PyPackagingBundle.message(message)
  },
  STRONG_EQ("==", "python.requirements.version.separator.strong.eq"),
  GTE(">=", "python.requirements.version.separator.gte"),
  COMPATIBLE("~=", "python.requirements.version.separator.compatible");

  override fun toString(): String = "${PyPackagingBundle.message(message)} (${separator}x.y.z)"
}