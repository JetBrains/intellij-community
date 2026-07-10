// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.linux

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal enum class LinuxWebKitBackend(val nativeId: Int) {
  X11(nativeId = 0),
  WaylandSnapshot(nativeId = 1),
}
