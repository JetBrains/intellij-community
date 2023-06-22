// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.move.java

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassHandler
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference

class MoveKotlinAliasClassHandler : MoveClassHandler {
    override fun doMoveClass(aClass: PsiClass, moveDestination: PsiDirectory): PsiClass? = null
    override fun getName(clazz: PsiClass?): String? = null

    override fun preprocessUsages(results: MutableCollection<UsageInfo>) {
        results.removeAll { usageInfo ->
            usageInfo is MoveRenameUsageInfo && (usageInfo.reference as? KtSimpleNameReference)?.getImportAlias() != null
        }
    }

    override fun prepareMove(aClass: PsiClass) {}

    override fun finishMoveClass(aClass: PsiClass) {}
}