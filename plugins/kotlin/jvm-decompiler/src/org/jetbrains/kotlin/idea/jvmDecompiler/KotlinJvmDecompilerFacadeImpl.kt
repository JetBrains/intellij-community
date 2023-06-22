// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.jvmDecompiler

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.kotlin.idea.internal.KotlinJvmDecompilerFacade
import org.jetbrains.kotlin.psi.KtFile

internal class KotlinJvmDecompilerFacadeImpl: KotlinJvmDecompilerFacade {
    override fun showDecompiledCode(sourceFile: KtFile) {
        ProgressManager.getInstance().run(KotlinBytecodeDecompilerTask(sourceFile))
    }
}
