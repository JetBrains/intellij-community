// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.analysis

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
enum class UNullability {
  NULLABLE, NOT_NULL, NULL, UNKNOWN;

  val isNullOrNullable: Boolean
    get() = this == NULL || this == NULLABLE
}