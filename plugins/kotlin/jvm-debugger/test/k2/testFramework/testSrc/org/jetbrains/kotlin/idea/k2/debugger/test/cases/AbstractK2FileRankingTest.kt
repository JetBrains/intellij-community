// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.debugger.test.cases

import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.idea.debugger.test.AbstractFileRankingTest

abstract class AbstractK2IdeK1CodeFileRankingTest : AbstractFileRankingTest()

abstract class AbstractK2IdeK2CodeFileRankingTest : AbstractK2IdeK1CodeFileRankingTest() {
    override val compileWithK2 = true

    override val lambdasGenerationScheme = JvmClosureGenerationScheme.INDY
}
