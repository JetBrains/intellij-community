// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.test

import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences
import org.jetbrains.kotlin.idea.debugger.test.util.SteppingInstruction
import org.jetbrains.kotlin.idea.debugger.test.util.SteppingInstructionKind

abstract class AbstractKotlinSteppingTest : KotlinDescriptorTestCaseWithStepping() {
    private enum class Category(val instruction: SteppingInstructionKind?) {
        StepIntoIgnoreFilters(SteppingInstructionKind.StepIntoIgnoreFilters),
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

    override fun setUp() {
        super.setUp()
        atDebuggerTearDown { category = null }
    }

    private fun doTest(path: String, category: Category) {
        this.category = category
        super.doTest(path)
    }

    override fun getK2IgnoreDirective(): String {
        return when {
            this::class.java.simpleName.endsWith("SmartStepInto") -> "// IGNORE_K2_SMART_STEP_INTO"
            else -> super.getK2IgnoreDirective()
        }
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