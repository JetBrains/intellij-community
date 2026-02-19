// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.debugger.test.cases

import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.idea.debugger.test.AbstractKotlinVariablePrintingTest

abstract class AbstractK2IdeK1CodeKotlinVariablePrintingTest : AbstractKotlinVariablePrintingTest()

abstract class AbstractK2IdeK2CodeKotlinVariablePrintingTest : AbstractK2IdeK1CodeKotlinVariablePrintingTest() {
    override val compileWithK2 = true

    override fun lambdasGenerationScheme() = JvmClosureGenerationScheme.INDY
}
