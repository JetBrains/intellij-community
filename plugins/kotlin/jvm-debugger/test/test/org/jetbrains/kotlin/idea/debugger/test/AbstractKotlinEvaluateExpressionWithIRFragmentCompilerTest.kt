/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.test

abstract class AbstractKotlinEvaluateExpressionWithIRFragmentCompilerTest : AbstractKotlinEvaluateExpressionTest() {
    override fun useIrBackend(): Boolean = false
    override fun fragmentCompilerBackend() =
        FragmentCompilerBackend.JVM_IR
}
