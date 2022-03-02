// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.test

import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences
import org.jetbrains.kotlin.idea.debugger.test.util.SteppingInstruction
import org.jetbrains.kotlin.idea.debugger.test.util.SteppingInstructionKind
import org.jetbrains.kotlin.test.TargetBackend

abstract class AbstractKotlinSteppingTest : KotlinDescriptorTestCaseWithStepping() {
    private enum class Category(val instruction: SteppingInstructionKind?) {
        StepInto(SteppingInstructionKind.StepInto),
        StepOut(SteppingInstructionKind.StepOut),
        StepOver(SteppingInstructionKind.StepOver),
        SmartStepInto(SteppingInstructionKind.SmartStepInto),
        Custom(null)
    }

    private var category: Category? = null

    protected fun doStepIntoTest(path: String) = doTest(path, Category.StepInto)
    protected fun doStepOutTest(path: String) = doTest(path, Category.StepOut)
    protected fun doStepOverTest(path: String) = doTest(path, Category.StepOver)
    protected fun doSmartStepIntoTest(path: String) = doTest(path, Category.SmartStepInto)
    protected fun doCustomTest(path: String) = doTest(path, Category.Custom)

    override fun tearDown() {
        category = null
        super.tearDown()
    }

    override fun targetBackend(): TargetBackend =
        TargetBackend.JVM_OLD

    private fun doTest(path: String, category: Category) {
        this.category = category
        super.doTest(path)
    }

    override fun doMultiFileTest(files: TestFiles, preferences: DebuggerPreferences) {
        val category = this.category ?: error("Category is not specified")
        val specificKind = category.instruction

        if (specificKind != null) {
            val instruction = SteppingInstruction.parseSingle(files.wholeFile, specificKind)
                ?: SteppingInstruction(specificKind, 1)

            process(listOf(instruction))
        } else {
            val instructions = SteppingInstruction.parse(files.wholeFile)
            process(instructions)
        }

        finish()
    }
}