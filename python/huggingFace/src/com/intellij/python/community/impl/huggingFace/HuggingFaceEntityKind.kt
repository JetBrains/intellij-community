// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class HuggingFaceEntityKind(val urlFragment: String, val printName: String) {
  MODEL("models", "model"),
  DATASET("datasets", "dataset"),
  SPACE("spaces", "space")
}
