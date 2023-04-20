// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.test

import org.jetbrains.kotlin.config.JvmClosureGenerationScheme

abstract class AbstractIrKotlinSteppingTest : AbstractKotlinSteppingTest() {
    override fun useIrBackend() = true
}

abstract class AbstractK1IdeK2CodeKotlinSteppingTest : AbstractIrKotlinSteppingTest() {
    override val compileWithK2 = true

    override fun lambdasGenerationScheme() = JvmClosureGenerationScheme.INDY
}
