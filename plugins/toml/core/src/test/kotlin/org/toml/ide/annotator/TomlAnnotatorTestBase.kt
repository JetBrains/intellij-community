/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.annotator

import com.intellij.ide.annotator.AnnotatorBase
import kotlin.reflect.KClass

abstract class TomlAnnotatorTestBase(private val annotatorClass: KClass<out AnnotatorBase>) : TomlAnnotationTestBase() {

    override fun createAnnotationFixture(): TomlAnnotationTestFixture =
        TomlAnnotationTestFixture(this, myFixture, annotatorClasses = listOf(annotatorClass))
}
