// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import org.jetbrains.kotlin.idea.refactoring.rename.AbstractInplaceRenameTest
import org.jetbrains.kotlin.idea.base.test.IgnoreTests


abstract class AbstractK2InplaceRenameTest : AbstractInplaceRenameTest() {

    override fun isFirPlugin(): Boolean = true

    override fun getAfterFileNameSuffix(): String? {
        return ".k2"
    }

    override fun doTest(unused: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(
            dataFilePath(),
            IgnoreTests.DIRECTIVES.IGNORE_K2,
            directivePosition = IgnoreTests.DirectivePosition.LAST_LINE_IN_FILE
        ) {
            doTestWithoutIgnoreDirective(unused)
        }
    }
}