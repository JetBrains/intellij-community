// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.fe10bindings.inspections

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.mock.MockProject
import com.intellij.psi.PsiFile
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.intentions.AbstractIntentionTestBase
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.io.File

abstract class AbstractFe10BindingIntentionTest : AbstractIntentionTestBase() {
    override fun isFirPlugin() = true

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    // left empty because error reporting in FIR and old FE is different
    override fun checkForErrorsBefore(fileText: String) {}
    override fun checkForErrorsAfter(fileText: String) {}

    override fun setUp() {
        super.setUp()
        project.registerLifetimeTokenFactoryForFe10Binding(myFixture.testRootDisposable)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }

    override fun doTestFor(mainFile: File, pathToFiles: Map<String, PsiFile>, intentionAction: IntentionAction, fileText: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(mainFile.toPath(), IgnoreTests.DIRECTIVES.IGNORE_FE10_BINDING_BY_FIR, "after") {
            super.doTestFor(mainFile, pathToFiles, intentionAction, fileText)
        }
    }
}