// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.debugger.test.cases

import org.jetbrains.kotlin.idea.debugger.test.AbstractBreakpointApplicabilityTest
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import java.nio.file.Paths

abstract class AbstractK2BreakpointApplicabilityTest : AbstractBreakpointApplicabilityTest()  {
    override fun isFirPlugin(): Boolean = true

    override fun doTest(unused: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(
            Paths.get(unused),
            IgnoreTests.DIRECTIVES.IGNORE_K2,
            directivePosition = IgnoreTests.DirectivePosition.LAST_LINE_IN_FILE
        ) {
            super.doTest(unused)
        }
    }
}
