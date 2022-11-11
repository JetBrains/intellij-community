// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.debugger.test.cases

import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.debugger.test.AbstractPositionManagerTest
import org.jetbrains.kotlin.idea.k2.debugger.test.withTestServicesNeededForCodeCompilation
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractK2PositionManagerTest : AbstractPositionManagerTest() {
    override fun isFirPlugin(): Boolean = true

    override fun getCompileFiles(files: MutableList<KtFile>?, configuration: CompilerConfiguration?): GenerationState {
        return withTestServicesNeededForCodeCompilation(project) {
            super.getCompileFiles(files, configuration)
        }
    }
}