// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.ide.IDETestContext

/**
 * Handles different stuff, that is related to that particular build tool
 */
open class BuildTool(val type: BuildToolType, val testContext: IDETestContext)