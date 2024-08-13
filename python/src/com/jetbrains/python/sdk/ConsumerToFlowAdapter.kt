// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.function.Consumer

@Service
private class MyService(val coroutineScope: CoroutineScope)

/**
 * collects first item of flow in EDT and calls [consumer] to be used as an adapter.
 */
@RequiresBlockingContext
internal fun <T : Any> Flow<T>.oneShotConsumer(consumer: Consumer<T>) {
  ApplicationManager.getApplication().service<MyService>().coroutineScope.launch(Dispatchers.EDT + ModalityState.defaultModalityState().asContextElement()) {
    consumer.accept(this@oneShotConsumer.first())
  }
}