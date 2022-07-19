// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.sdk

import java.nio.file.Path

data class SdkObject(
  val sdkName: String,
  val sdkType: String,
  val sdkPath: Path,
)