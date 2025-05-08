// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging

import com.jetbrains.python.PyBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal

enum class PyRequirementsVersionSpecifierType(val separator: String, val message: String) {
  NO_VERSION("", "python.requirements.version.separator.no.version") {
    override fun toString() = PyBundle.message(message)
  },
  STRONG_EQ("==", "python.requirements.version.separator.strong.eq"),
  GTE(">=", "python.requirements.version.separator.gte"),
  COMPATIBLE("~=", "python.requirements.version.separator.compatible");

  override fun toString(): String = "${PyBundle.message(message)} (${separator}x.y.z)"
}