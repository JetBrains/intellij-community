// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.impl.linux

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal object LinuxWaylandWindowUtil {
  fun isSupportedToolkit(): Boolean = SystemInfoRt.isLinux && StartupUiUtil.isWaylandToolkit()
}
