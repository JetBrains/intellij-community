// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test.mock

import com.jetbrains.jdi.MockLocalVariable
import com.sun.jdi.VirtualMachine

// Container for the debug information that's relevant to Kotlin in a JDI Method.
class MockMethodInfo(
    val name: String,
    val sourceNames: Array<String>,
    val sourcePaths: Array<String?>,
    val variableNames: Array<String>,
    // Each line is encoded with 4 ints: codeIndex, outputLineNumber, lineNumber, sourceNameIndex
    val allLineLocations: IntArray,
    // Each local variable is encoded with 4 ints: startPc, length, slot, variableNameIndex
    val localVariableTable: IntArray,
    // KotlinDebug segment encoded with 3 ints per line: lineIndex, lineNumber, sourceNameIndex
    val kotlinDebugSegment: IntArray,
) {
    private fun lineCount(): Int = allLineLocations.size / 4
    private fun lineCodeIndex(id: Int): Int = allLineLocations[4 * id]
    private fun outputLineNumber(id: Int): Int = allLineLocations[4 * id + 1]
    private fun lineNumber(id: Int): Int = allLineLocations[4 * id + 2]
    private fun lineSourceName(id: Int): String = sourceNames[allLineLocations[4 * id + 3]]
    private fun lineSourcePath(id: Int): String? = sourcePaths[allLineLocations[4 * id + 3]]
    private fun variableCount(): Int = localVariableTable.size / 4
    private fun variableStartPc(id: Int): Int = localVariableTable[4 * id]
    private fun variableLength(id: Int): Int = localVariableTable[4 * id + 1]
    private fun variableSlot(id: Int): Int = localVariableTable[4 * id + 2]
    private fun variableName(id: Int): String = variableNames[localVariableTable[4 * id + 3]]
    private fun kotlinDebugEntries(): Int = kotlinDebugSegment.size / 3
    private fun kotlinDebugLineIndex(id: Int): Int = kotlinDebugSegment[3 * id]
    private fun kotlinDebugLineNumber(id: Int): Int = kotlinDebugSegment[3 * id + 1]
    private fun kotlinDebugSourceName(id: Int): String = sourceNames[kotlinDebugSegment[3 * id + 2]]
    private fun kotlinDebugSourcePath(id: Int): String? = sourcePaths[kotlinDebugSegment[3 * id + 2]]

    fun toMockMethod(virtualMachine: VirtualMachine): MockMethod {
        val method = MockMethod(name, virtualMachine)

        // Generate all line locations
        val allLineLocations = mutableListOf<MockLocation>()
        for (i in 0 until lineCount()) {
            allLineLocations += MockLocation(
                codeIndex = lineCodeIndex(i),
                lineNumber = lineNumber(i),
                sourceName = lineSourceName(i),
                sourcePath = lineSourcePath(i),
                outputLineNumber = outputLineNumber(i),
                method = method
            )
        }

        // Add KotlinDebug information
        for (i in 0 until kotlinDebugEntries()) {
            val location = allLineLocations[kotlinDebugLineIndex(i)]
            location.kotlinDebugLineNumber = kotlinDebugLineNumber(i)
            location.kotlinDebugSourceName = kotlinDebugSourceName(i)
            location.kotlinDebugSourcePath = kotlinDebugSourcePath(i)
        }

        // Add local variables
        val variables = mutableListOf<MockLocalVariable>()
        for (i in 0 until variableCount()) {
            fun pcToLocation(pc: Int): MockLocation {
                // First try to find a location before pc.
                allLineLocations.lastOrNull { it.codeIndex().toInt() <= pc }?.let {
                    return it.withCodeIndex(pc)
                }
                // Otherwise, we pick the first location in the method
                return allLineLocations.first().withCodeIndex(pc)
            }

            val startPc = variableStartPc(i)
            val length = variableLength(i)
            val nameAndDescriptor = variableName(i)

            variables += MockLocalVariable(
                startPc = startPc,
                length = length,
                name = nameAndDescriptor.substringBefore(':'),
                descriptor = nameAndDescriptor.substringAfter(':'),
                slot = variableSlot(i),
                scopeStart = pcToLocation(startPc),
                scopeEnd = pcToLocation(startPc + length - 1), // see [ConcreteMethodImpl.createVariables1_4]
                method = method
            )
        }

        method.updateContents(allLineLocations, variables)
        return method
    }

    // Produce a human-readable trace of the debug information in this method.
    // This is supposed to be used from the debugger to inspect a MockMethodInfo.
    @Suppress("unused")
    fun asString(): String {
        val codeIndexToEvents = mutableMapOf<Int, MutableList<String>>()
        fun emit(codeIndex: Int, line: String) {
            codeIndexToEvents.getOrPut(codeIndex) { mutableListOf() } += line
        }

        for (id in 0 until lineCount()) {
            emit(lineCodeIndex(id), " Line ${lineSourceName(id)}:${lineNumber(id)} (${outputLineNumber(id)})")
        }

        for (id in 0 until kotlinDebugEntries()) {
            val debugOffset = lineCodeIndex(kotlinDebugLineIndex(id))
            val location = "${kotlinDebugSourceName(id)}:${kotlinDebugLineNumber(id)}"
            emit(debugOffset, " Scope $location")
        }

        for (id in 0 until variableCount()) {
            val startOffset = variableStartPc(id)
            val endOffset = startOffset + variableLength(id)
            val name = "${variableName(id)}@${variableSlot(id)}"
            emit(endOffset, "-Local $name")
            emit(startOffset, "+Local $name")
        }

        val pcLength = codeIndexToEvents.keys.maxOf { it.toString().length }
        return buildString {
            for ((pc, lines) in codeIndexToEvents.entries.sortedBy { it.key }) {
                appendLine("${pc.toString().padStart(pcLength)} ${lines[0]}")
                for (line in lines.drop(1)) {
                    appendLine("${" ".repeat(pcLength)} ${line}")
                }
            }
        }
    }
}
