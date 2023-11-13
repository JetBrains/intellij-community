// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassHandler
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.asJava.classes.KtLightClass

class MoveKotlinClassHandler : MoveClassHandler {
    override fun doMoveClass(aClass: PsiClass, moveDestination: PsiDirectory): PsiClass? = null
    override fun getName(clazz: PsiClass?): String? = null

    /**
     * Ensure that Kotlin classes are not processed twice when moving Kotlin files
     * (once through the Java move handler calling KtFile.getClasses() and another time through the Kotlin move handler)
     */
    override fun preprocessUsages(results: MutableCollection<UsageInfo>) {
        results.removeAll { usageInfo ->
          usageInfo is MoveRenameUsageInfo && usageInfo.referencedElement is KtLightClass
        }
    }

    override fun prepareMove(aClass: PsiClass) {}

    override fun finishMoveClass(aClass: PsiClass) {}
}