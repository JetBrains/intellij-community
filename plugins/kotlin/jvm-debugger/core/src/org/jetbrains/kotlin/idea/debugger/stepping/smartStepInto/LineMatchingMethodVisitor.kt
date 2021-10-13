// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import org.jetbrains.org.objectweb.asm.Label

abstract class LineMatchingMethodVisitor(
    private val zeroBasedLineNumbers: ClosedRange<Int>
) : OpcodeReportingMethodVisitor() {
    protected var lineMatches = false
    protected var currentLine = 0
    protected var lineEverMatched = false

    override fun visitLineNumber(line: Int, start: Label) {
        currentLine = line
        lineMatches = zeroBasedLineNumbers.contains(line - 1)
        if (lineMatches) {
            lineEverMatched = true
        }
    }

    override fun visitCode() {
        lineEverMatched = false
        lineMatches = false
        currentLine = 0
    }
}
