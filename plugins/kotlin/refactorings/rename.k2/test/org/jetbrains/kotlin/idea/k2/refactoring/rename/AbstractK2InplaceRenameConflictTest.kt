// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.refactoring.rename.AbstractInplaceRenameTest
import org.jetbrains.kotlin.idea.refactoring.rename.checkMarkedRenameResult
import org.jetbrains.kotlin.psi.KtFile


abstract class AbstractK2InplaceRenameConflictTest : AbstractInplaceRenameTest() {

    override fun checkAfterRename(file: PsiFile) {
        checkMarkedRenameResult(file as KtFile)
    }
}