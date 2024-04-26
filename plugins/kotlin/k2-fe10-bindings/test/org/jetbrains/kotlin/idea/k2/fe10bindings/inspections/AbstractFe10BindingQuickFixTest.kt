// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.fe10bindings.inspections

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.base.test.IgnoreTests

abstract class AbstractFe10BindingQuickFixTest : AbstractQuickFixTest() {
    override fun isFirPlugin(): Boolean = true

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override val disableTestDirective: String get() = IgnoreTests.DIRECTIVES.IGNORE_FE10_BINDING_BY_FIR

    override fun setUp() {
        super.setUp()
        project.registerLifetimeTokenFactoryForFe10Binding(myFixture.testRootDisposable)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { runInEdtAndWait { project.invalidateCaches() } },
            ThrowableRunnable { super.tearDown() }
        )
    }

    // TODO: Enable these as more actions/inspections are enabled, and/or add more FIR-specific directives
    override fun checkForUnexpectedErrors() {}
    override fun checkAvailableActionsAreExpected(actions: List<IntentionAction>) {}
}