// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.module

import com.intellij.openapi.module.Module
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import org.jetbrains.annotations.ApiStatus

@get:ApiStatus.Internal
val Module.eelDescriptor: EelDescriptor get() = project.getEelDescriptor()

@ApiStatus.Internal
suspend fun Module.getEel(): EelApi = eelDescriptor.toEelApi()
