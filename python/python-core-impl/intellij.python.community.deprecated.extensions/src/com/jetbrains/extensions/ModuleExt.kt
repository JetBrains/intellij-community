// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.extensions

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval

@Deprecated(message = "Moved to com.jetbrains.python")
@ScheduledForRemoval
fun Module.getSdk(): Sdk? = ModuleRootManager.getInstance(this).sdk
