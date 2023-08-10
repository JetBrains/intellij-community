// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base.searching.usages

import com.intellij.usages.Usage
import com.intellij.usages.rules.ImportFilteringRule
import com.intellij.usages.rules.PsiElementUsage
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class KotlinImportFilteringRule : ImportFilteringRule() {
    override fun isVisible(usage: Usage): Boolean {
        if (usage is PsiElementUsage) {
            return usage.element?.getNonStrictParentOfType<KtImportDirective>() == null
        }

        return true
    }
}
