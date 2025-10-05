// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv

import com.google.gson.annotations.SerializedName

internal data class PipFileLock(
  @SerializedName("_meta") var meta: PipFileLockMeta?,
  @SerializedName("default") var packages: Map<String, PipFileLockPackage>?,
  @SerializedName("develop") var devPackages: Map<String, PipFileLockPackage>?,
)