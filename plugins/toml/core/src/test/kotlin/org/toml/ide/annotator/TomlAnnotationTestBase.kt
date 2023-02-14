/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.annotator

import org.intellij.lang.annotations.Language
import org.toml.TomlTestBase

abstract class TomlAnnotationTestBase : TomlTestBase() {

    private lateinit var annotationFixture: TomlAnnotationTestFixture

    override fun setUp() {
        super.setUp()
        annotationFixture = createAnnotationFixture()
        annotationFixture.setUp()
    }

    override fun tearDown() {
        try {
            annotationFixture.tearDown()
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    protected abstract fun createAnnotationFixture(): TomlAnnotationTestFixture

    protected fun checkHighlighting(@Language("TOML") text: String, ignoreExtraHighlighting: Boolean = true) =
        annotationFixture.checkHighlighting(text, ignoreExtraHighlighting)

    protected fun checkByText(
        @Language("TOML") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        ignoreExtraHighlighting: Boolean = false
    ) = annotationFixture.checkByText(text, checkWarn, checkInfo, checkWeakWarn, ignoreExtraHighlighting)

    protected fun checkFixByText(
        fixName: String,
        @Language("TOML") before: String,
        @Language("TOML") after: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false
    ) = annotationFixture.checkFixByText(fixName, before, after, checkWarn, checkInfo, checkWeakWarn)

    protected fun checkFixByTextWithoutHighlighting(
        fixName: String,
        @Language("TOML") before: String,
        @Language("TOML") after: String,
    ) = annotationFixture.checkFixByTextWithoutHighlighting(fixName, before, after)
}
