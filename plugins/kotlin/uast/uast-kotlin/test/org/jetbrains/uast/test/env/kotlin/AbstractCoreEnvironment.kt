// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.env.kotlin

import com.intellij.mock.MockProject
import java.io.File

abstract class AbstractCoreEnvironment {
    abstract val project: MockProject

    open fun dispose() {
        // Do nothing
    }

    abstract fun addJavaSourceRoot(root: File)
    abstract fun addJar(root: File)
}