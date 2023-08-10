// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.debugger.test.cases

import org.jetbrains.kotlin.idea.debugger.test.AbstractClassNameCalculatorTest
import org.jetbrains.kotlin.idea.k2.debugger.test.withTestServicesNeededForCodeCompilation
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractK2ClassNameCalculatorTest: AbstractClassNameCalculatorTest() {
    override fun isFirPlugin(): Boolean = true

    override fun checkConsistency(file: KtFile, allNames: Map<KtElement, String>) {
        withTestServicesNeededForCodeCompilation(project) {
            super.checkConsistency(file, allNames)
        }
    }
}