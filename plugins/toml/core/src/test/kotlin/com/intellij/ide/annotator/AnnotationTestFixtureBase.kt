/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package com.intellij.ide.annotator

import com.intellij.codeInsight.daemon.impl.SeveritiesProvider
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.openapiext.Testmark
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.BaseFixture
import kotlin.reflect.KClass

abstract class AnnotationTestFixtureBase(
    protected val codeInsightFixture: CodeInsightTestFixture,
    private val annotatorClasses: List<KClass<out AnnotatorBase>> = emptyList(),
    private val inspectionClasses: List<KClass<out InspectionProfileEntry>> = emptyList()
) : BaseFixture() {

    val project: Project get() = codeInsightFixture.project
    lateinit var enabledInspections: List<InspectionProfileEntry>

    protected abstract val baseFileName: String

    override fun setUp() {
        super.setUp()
        annotatorClasses.forEach { AnnotatorBase.enableAnnotator(it.java, testRootDisposable) }
        enabledInspections = InspectionTestUtil.instantiateTools(inspectionClasses.map { it.java })
        codeInsightFixture.enableInspections(*enabledInspections.toTypedArray())
    }

    protected fun replaceCaretMarker(text: String) = text.replace("/*caret*/", "<caret>")

    fun checkHighlighting(text: String) =
        checkByText(text, checkWarn = false, checkWeakWarn = false, checkInfo = false, ignoreExtraHighlighting = true)
    fun checkInfo(text: String) = checkByText(text, checkWarn = false, checkWeakWarn = false, checkInfo = true)
    fun checkWarnings(text: String) = checkByText(text, checkWarn = true, checkWeakWarn = true, checkInfo = false)
    fun checkErrors(text: String) = checkByText(text, checkWarn = false, checkWeakWarn = false, checkInfo = false)

    protected open fun configureByText(text: String) {
        codeInsightFixture.configureByText(baseFileName, replaceCaretMarker(text.trimIndent()))
    }

    fun checkByText(
        text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        ignoreExtraHighlighting: Boolean = false,
        testmark: Testmark? = null
    ) = check(text,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        ignoreExtraHighlighting = ignoreExtraHighlighting,
        configure = this::configureByText,
        testmark = testmark)

    fun checkFixByText(
        fixName: String,
        before: String,
        after: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        testmark: Testmark? = null
    ) = checkFix(fixName, before, after,
        configure = this::configureByText,
        checkBefore = { codeInsightFixture.checkHighlighting(checkWarn, checkInfo, checkWeakWarn) },
        checkAfter = this::checkByText,
        testmark = testmark)

    fun checkFixIsUnavailable(
        fixName: String,
        text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        ignoreExtraHighlighting: Boolean = false,
        testmark: Testmark? = null
    ) = checkFixIsUnavailable(fixName, text,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        ignoreExtraHighlighting = ignoreExtraHighlighting,
        configure = this::configureByText,
        testmark = testmark)

    protected fun checkFixIsUnavailable(
        fixName: String,
        text: String,
        checkWarn: Boolean,
        checkInfo: Boolean,
        checkWeakWarn: Boolean,
        ignoreExtraHighlighting: Boolean,
        configure: (String) -> Unit,
        testmark: Testmark? = null
    ) {
        check(text, checkWarn, checkInfo, checkWeakWarn, ignoreExtraHighlighting, configure, testmark)
        check(codeInsightFixture.filterAvailableIntentions(fixName).isEmpty()) {
            "Fix $fixName should not be possible to apply."
        }
    }

    fun checkFixByTextWithoutHighlighting(
        fixName: String,
        before: String,
        after: String,
        testmark: Testmark? = null
    ) = checkFix(fixName, before, after,
        configure = this::configureByText,
        checkBefore = {},
        checkAfter = this::checkByText,
        testmark = testmark)

    protected open fun check(
        text: String,
        checkWarn: Boolean,
        checkInfo: Boolean,
        checkWeakWarn: Boolean,
        ignoreExtraHighlighting: Boolean,
        configure: (String) -> Unit,
        testmark: Testmark? = null
    ) {
        val action: () -> Unit = {
            configure(text)
            codeInsightFixture.checkHighlighting(checkWarn, checkInfo, checkWeakWarn, ignoreExtraHighlighting)
        }
        testmark?.checkHit(action) ?: action()
    }

    protected open fun checkFix(
        fixName: String,
        before: String,
        after: String,
        configure: (String) -> Unit,
        checkBefore: () -> Unit,
        checkAfter: (String) -> Unit,
        testmark: Testmark? = null
    ) {
        val action: () -> Unit = {
            configure(before)
            checkBefore()
            applyQuickFix(fixName)
            checkAfter(after)
        }
        testmark?.checkHit(action) ?: action()
    }

    private fun checkByText(text: String) {
        codeInsightFixture.checkResult(replaceCaretMarker(text.trimIndent()))
    }

    private fun applyQuickFix(name: String) {
        val action = codeInsightFixture.findSingleIntention(name)
        codeInsightFixture.launchAction(action)
    }

    fun registerSeverities(severities: List<HighlightSeverity>) {
        val testSeverityProvider = TestSeverityProvider(severities)
        SeveritiesProvider.EP_NAME.getPoint(null).registerExtension(testSeverityProvider, testRootDisposable)
    }
}
