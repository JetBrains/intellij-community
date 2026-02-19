// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import java.io.File

abstract class AbstractKotlinValuesHintsProviderTest : AbstractKotlinInlayHintsProviderTest() {


    override fun doTestProviders(file: File) {
        val fileContents = FileUtil.loadFile(file, true)
        withCustomCompilerOptions(fileContents, project, module) {
            super.doTestProviders(file)
        }
    }
}