// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.run

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.junit.JunitKotlinTestFrameworkProvider

@Deprecated("Class is moved to the org.jetbrains.kotlin.idea.junit package.", level = DeprecationLevel.ERROR)
class KotlinJUnitRunConfigurationProducer {
    companion object {
        @Deprecated(
            "Use getJavaTestEntity() instead.",
            level = DeprecationLevel.ERROR,
            replaceWith = ReplaceWith(
                "JunitKotlinTestFrameworkProvider.getJavaTestEntity(leaf, checkMethod = false)?.testClass",
                "org.jetbrains.kotlin.idea.junit.JunitKotlinTestFrameworkProvider"
            )
        )
        fun getTestClass(leaf: PsiElement): PsiClass? {
            return JunitKotlinTestFrameworkProvider.getJavaTestEntity(leaf, checkMethod = false)?.testClass
        }
    }
}