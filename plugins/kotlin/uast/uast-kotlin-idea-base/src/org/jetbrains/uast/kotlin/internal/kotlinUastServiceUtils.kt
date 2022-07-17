// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.internal

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.facet.JvmOnlyProjectChecker
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.util.caching.get
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtElement

val PsiElement.isJvmElement: Boolean
    get() {
        if (JvmOnlyProjectChecker.getInstance(project).get()) {
            return true
        } else if (this is KtElement) {
            return platform.isJvm()
        } else {
            val module = module ?: return true
            return module.platform.isJvm()
        }
    }