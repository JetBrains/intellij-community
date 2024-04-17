// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object HuggingFaceConstants {
  const val HF_API_FETCH_PAGE_SIZE = 500
  const val MAX_MODELS_IN_CACHE = HF_API_FETCH_PAGE_SIZE * 20
  const val MAX_DATASETS_IN_CACHE = HF_API_FETCH_PAGE_SIZE * 20
  const val API_FETCH_SORT_KEY = "likes"
  const val DATASET_FAKE_PIPELINE_TAG = "dataset"
  const val UNDEFINED_PIPELINE_TAG = "undefined"
  const val NO_PIPELINE_TAG = "none"
}
