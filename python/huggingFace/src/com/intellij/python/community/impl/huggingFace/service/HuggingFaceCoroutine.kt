// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.annotations.ApiStatus

@Service
@ApiStatus.Internal
class HuggingFaceCoroutine(coroutineScope: CoroutineScope) {
  val ioScope: CoroutineScope = coroutineScope.childScope("HuggingFace IO Scope", context = Dispatchers.IO)
  val edtScope: CoroutineScope = coroutineScope.childScope("HuggingFace EDT Scope", context = Dispatchers.EDT)

  object Utils {
    val ioScope: CoroutineScope
      get() = ApplicationManager.getApplication().service<HuggingFaceCoroutine>().ioScope

    val edtScope: CoroutineScope
      get() = ApplicationManager.getApplication().service<HuggingFaceCoroutine>().edtScope
  }
}
