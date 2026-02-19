// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.openapi.application.readAction
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.base.psi.getStartLineOffset
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto.KotlinMethodSmartStepTarget
import org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto.KotlinSmartStepIntoHandler
import org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto.targetsWithDeclaration
import org.jetbrains.kotlin.idea.debugger.test.mock.MockSourcePosition
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

abstract class AbstractSmartStepIntoTest : KotlinLightCodeInsightFixtureTestCase() {
    private val fixture: JavaCodeInsightTestFixture
        get() = myFixture

    protected open fun doTest(path: String) = runBlocking {
        fixture.configureByFile(fileName())

        val offset = fixture.caretOffset
        val line = fixture.getDocument(fixture.file!!)!!.getLineNumber(offset)

        val lineStart = getStartLineOffset(file, line)!!
        val elementAtOffset = file.findElementAt(lineStart)

        val position = MockSourcePosition(
            myFile = fixture.file,
            myLine = line,
            myOffset = offset,
            myEditor = fixture.editor,
            myElementAt = elementAtOffset
        )

        val actual = withContext(Dispatchers.Default) {
            val targets = KotlinSmartStepIntoHandler().findSmartStepTargets(position)
            readAction {
                targets.map { target ->
                    val suffix = if (target is KotlinMethodSmartStepTarget && targets.targetsWithDeclaration(target.getDeclaration()).count() > 1) {
                        "_${target.methodInfo.ordinal}"
                    } else ""
                    "${target.presentation}$suffix"
                }
            }
        }

        val expected = InTextDirectivesUtils.findListWithPrefixes(fixture.file?.text!!.replace("\\,", "+++"), "// EXISTS: ")
            .map { it.replace("+++", ",") }

        assert(expected == actual) {
            actual.firstOrNull { it !in expected }?.let { actualTargetName ->
                "Unexpected step into target was found: $actualTargetName\n${renderTableWithResults(expected, actual)}" +
                        "\n // EXISTS: ${actual.joinToString()}"
            } ?:
            expected.firstOrNull { it !in actual }?.let { expectedTargetName ->
                "Missed step into target: $expectedTargetName\n${renderTableWithResults(expected, actual)}" +
                        "\n // EXISTS: ${actual.joinToString()}"
            } ?: "The order of smart step targets is different\n  // EXISTS: ${actual.joinToString()}"
        }
    }

    private fun renderTableWithResults(expected: List<String>, actual: List<String>): String {
        val sb = StringBuilder()

        val maxExtStrSize = listOf(expected, actual).maxOf { l -> l.maxOfOrNull { it.length } ?: 0 } + 5
        val longerList = (if (expected.size < actual.size) actual else expected).sorted()
        val shorterList = (if (expected.size < actual.size) expected else actual).sorted()
        for ((i, element) in longerList.withIndex()) {
            sb.append(element)
            sb.append(" ".repeat(maxExtStrSize - element.length))
            if (i < shorterList.size) sb.append(shorterList[i])
            sb.append("\n")
        }

        return sb.toString()
    }

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
}
