// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.debugger.test.cases

import org.jetbrains.kotlin.config.JvmClosureGenerationScheme

abstract class AbstractK2IndyLambdaKotlinSteppingTest : AbstractK2IrKotlinSteppingTest() {
    override fun lambdasGenerationScheme() = JvmClosureGenerationScheme.INDY
}