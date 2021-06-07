// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.testng

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiClassUtil
import com.theoryinpractice.testng.util.TestNGUtil
import org.jetbrains.kotlin.idea.extensions.KotlinTestFrameworkProvider

object TestNgKotlinTestFrameworkProvider : KotlinTestFrameworkProvider {
    override val canRunJvmTests: Boolean
        get() = true

    override fun isTestJavaClass(testClass: PsiClass): Boolean {
        return PsiClassUtil.isRunnableClass(testClass, true, false) && TestNGUtil.hasTest(testClass)
    }

    override fun isTestJavaMethod(testMethod: PsiMethod): Boolean {
        return TestNGUtil.hasTest(testMethod)
    }
}