// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.api

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class HuggingFaceModelSortKey(val value: String, val displayName: String) {
  LIKES("likes", "Likes"),
  DOWNLOADS("downloads", "Downloads"),
  CREATED_AT("createdAt", "Created At"),
  LAST_MODIFIED("lastModified", "Last Modified")
}
