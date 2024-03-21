// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.annotation

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal @JvmInline value class HuggingFaceModelIdentifier(val value: String)
@ApiStatus.Internal @JvmInline value class HuggingFaceDatasetIdentifier(val value: String)
@ApiStatus.Internal @JvmInline value class HuggingFaceEntityIdentifier(val value: String)
