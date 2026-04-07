// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.debugger.test.cases

import com.intellij.debugger.engine.INSTRUMENTATION_CONDITION_HIT_CALLBACK
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.idea.debugger.test.AbstractIrKotlinSteppingTest

abstract class AbstractK2IdeK1CodeKotlinSteppingTest : AbstractIrKotlinSteppingTest()

abstract class AbstractK2IdeK2CodeKotlinSteppingTest : AbstractK2IdeK1CodeKotlinSteppingTest() {
    override val compileWithK2 = true

    override fun lambdasGenerationScheme() = JvmClosureGenerationScheme.INDY
}

abstract class AbstractK2IdeK2CodeKotlinInstrumentationConditionsTest : AbstractK2IdeK2CodeKotlinSteppingTest() {
    override fun setUp() {
        super.setUp()
        val enabledByDefault = Registry.`is`("debugger.breakpoint.instrumentation")
        Registry.get("debugger.breakpoint.instrumentation").setValue(true)
        atDebuggerTearDown {
            Registry.get("debugger.breakpoint.instrumentation").setValue(enabledByDefault)
        }
    }

    override fun createLocalProcess(className: String?) {
        super.createLocalProcess(className)
        debugProcess.putUserData(INSTRUMENTATION_CONDITION_HIT_CALLBACK) {
            println("Instrumentation hit", ProcessOutputTypes.SYSTEM)
        }
    }
}
