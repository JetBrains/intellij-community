/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.annotator

import com.intellij.openapiext.Testmark
import org.intellij.lang.annotations.Language
import org.toml.TomlTestBase

abstract class TomlAnnotationTestBase : TomlTestBase() {

    protected lateinit var annotationFixture: TomlAnnotationTestFixture

    override fun setUp() {
        super.setUp()
        annotationFixture = createAnnotationFixture()
        annotationFixture.setUp()
    }

    override fun tearDown() {
        annotationFixture.tearDown()
        super.tearDown()
    }

    protected abstract fun createAnnotationFixture(): TomlAnnotationTestFixture

    protected fun checkHighlighting(@Language("TOML") text: String, ignoreExtraHighlighting: Boolean = true) =
        annotationFixture.checkHighlighting(text, ignoreExtraHighlighting)

    protected fun checkByText(
        @Language("TOML") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        ignoreExtraHighlighting: Boolean = false,
        testmark: Testmark? = null
    ) = annotationFixture.checkByText(text, checkWarn, checkInfo, checkWeakWarn, ignoreExtraHighlighting, testmark)

    protected fun checkFixByText(
        fixName: String,
        @Language("TOML") before: String,
        @Language("TOML") after: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        testmark: Testmark? = null
    ) = annotationFixture.checkFixByText(fixName, before, after, checkWarn, checkInfo, checkWeakWarn, testmark)

    protected fun checkFixByTextWithoutHighlighting(
        fixName: String,
        @Language("TOML") before: String,
        @Language("TOML") after: String,
        testmark: Testmark? = null
    ) = annotationFixture.checkFixByTextWithoutHighlighting(fixName, before, after, testmark)
}
