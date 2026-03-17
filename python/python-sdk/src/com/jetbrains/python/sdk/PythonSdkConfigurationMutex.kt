// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import org.jetbrains.annotations.ApiStatus

/**
 * Global mutex that serializes all Python SDK configuration operations — both
 * auto-configuration on startup and manual SDK creation from the UI.
 *
 * This is intentionally global (not per-module) because setting an SDK on one module
 * can affect others via inherited project SDK in multi-module workspaces.
 *
 * Observe [isLocked][ObservableMutex.isLocked] to track whether an SDK configuration is running.
 */
@ApiStatus.Internal
val pythonSdkConfigurationMutex: ObservableMutex = ObservableMutex()
