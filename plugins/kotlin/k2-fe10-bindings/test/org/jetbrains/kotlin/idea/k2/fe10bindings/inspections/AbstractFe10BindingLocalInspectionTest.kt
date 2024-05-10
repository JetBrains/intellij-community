// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.fe10bindings.inspections

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.inspections.AbstractLocalInspectionTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import java.io.File

abstract class AbstractFe10BindingLocalInspectionTest : AbstractLocalInspectionTest() {

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun checkForUnexpectedErrors(fileText: String) {}

    override fun setUp() {
        super.setUp()
        project.registerLifetimeTokenFactoryForFe10Binding(myFixture.testRootDisposable)
    }

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    override fun doTestFor(mainFile: File, inspection: LocalInspectionTool, fileText: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(mainFile.toPath(), IgnoreTests.DIRECTIVES.IGNORE_FE10_BINDING_BY_FIR, "after") {
            doTestForInternal(mainFile, inspection, fileText)
        }
    }
}