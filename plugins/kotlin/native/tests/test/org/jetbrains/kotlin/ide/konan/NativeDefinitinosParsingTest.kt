// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.ide.konan

import com.intellij.testFramework.ParsingTestCase
import org.jetbrains.kotlin.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.util.slashedPath

class NativeDefinitionsParsingTest : ParsingTestCase("", "def", NativeDefinitionsParserDefinition()) {

    fun testAllProperties() = doTest(true)

    fun testBadDefinitions() = doTest(true)

    override fun getTestDataPath(): String = KotlinRoot.DIR.resolve("native/tests/testData/colorHighlighting").slashedPath

    override fun skipSpaces(): Boolean = false

    override fun includeRanges(): Boolean = true
}