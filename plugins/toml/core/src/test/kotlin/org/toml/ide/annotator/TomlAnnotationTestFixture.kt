/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.annotator

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import junit.framework.TestCase
import kotlin.reflect.KClass

class TomlAnnotationTestFixture(
    testCase: TestCase,
    codeInsightFixture: CodeInsightTestFixture,
    annotatorClasses: List<KClass<out AnnotatorBase>> = emptyList(),
    inspectionClasses: List<KClass<out InspectionProfileEntry>> = emptyList()
) : AnnotationTestFixtureBase(testCase, codeInsightFixture, annotatorClasses, inspectionClasses) {
    override val baseFileName: String = "example.toml"
}
