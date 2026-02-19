// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import org.jetbrains.kotlin.idea.refactoring.rename.AbstractInplaceRenameTest


abstract class AbstractK2InplaceRenameTest : AbstractInplaceRenameTest() {

    override fun getAfterFileNameSuffix(): String? {
        return ".k2"
    }
}