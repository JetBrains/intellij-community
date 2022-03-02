// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.kotlin

import org.jetbrains.uast.UFile
import org.jetbrains.uast.test.common.ResolveTestBase
import org.junit.Test

class KotlinUastResolveTest : AbstractKotlinUastTest(), ResolveTestBase {
    override fun check(testName: String, file: UFile) {
        super.check(testName, file)
    }

    @Test fun testMethodReference() = doTest("MethodReference")
}
