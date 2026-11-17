// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.stubs

import com.google.common.collect.RangeSet
import com.intellij.openapi.util.Version
import org.jetbrains.annotations.ApiStatus

interface PyVersionSpecificStub {
  @get:ApiStatus.Internal
  val versions: RangeSet<Version>

  /**
   * Returns whether the element of this stub exists on [version]. Prefer it to `versions.contains(version)`, which
   * allocates on each call.
   */
  @ApiStatus.Internal
  fun containsVersion(version: Version): Boolean = versions.contains(version)
}
