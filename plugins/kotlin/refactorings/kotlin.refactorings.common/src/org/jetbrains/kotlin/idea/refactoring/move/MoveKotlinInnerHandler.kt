// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.refactoring.move.moveInner.MoveJavaInnerHandler
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference

class MoveKotlinInnerHandler : MoveJavaInnerHandler() {
    override fun preprocessUsages(results: MutableCollection<UsageInfo>?) {
        results?.removeAll { usageInfo ->
            usageInfo.element?.references?.any { reference ->
              reference is KtSimpleNameReference && reference.getImportAlias() != null
            } == true
        }
    }
}