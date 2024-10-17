// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.debugger.test.cases

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.debugger.test.AbstractFlowAsyncStackTraceTest

abstract class AbstractK2FlowAsyncStackTraceTest : AbstractFlowAsyncStackTraceTest() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2
}
