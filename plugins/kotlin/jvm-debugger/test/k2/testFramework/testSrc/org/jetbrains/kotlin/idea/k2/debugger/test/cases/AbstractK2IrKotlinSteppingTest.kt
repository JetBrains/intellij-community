// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.debugger.test.cases

import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.idea.debugger.test.AbstractIrKotlinSteppingTest

abstract class AbstractK2IdeK1CodeKotlinSteppingTest : AbstractIrKotlinSteppingTest()

abstract class AbstractK2IdeK2CodeKotlinSteppingTest : AbstractK2IdeK1CodeKotlinSteppingTest() {
    override val compileWithK2 = true

    override fun lambdasGenerationScheme() = JvmClosureGenerationScheme.INDY
}
