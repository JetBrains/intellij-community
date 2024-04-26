// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import org.jetbrains.kotlin.idea.refactoring.rename.AbstractMultiModuleRenameTest

abstract class AbstractFirMultiModuleRenameTest: AbstractMultiModuleRenameTest() {
    override fun isFirPlugin(): Boolean {
        return true
    }
}