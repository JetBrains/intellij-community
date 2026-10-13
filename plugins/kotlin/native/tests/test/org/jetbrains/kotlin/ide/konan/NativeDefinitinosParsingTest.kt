// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.ide.konan

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.parsingFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.util.slashedPath
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class NativeDefinitionsParsingTest {
    companion object {
        private val projectFixture = projectFixture()
    }

    private val parsing by parsingFixture(
        projectFixture,
        KotlinRoot.DIR.resolve("native/tests/testData/colorHighlighting").slashedPath + "/",
        "def",
        definitions = arrayOf(NativeDefinitionsParserDefinition()),
    )

    @BeforeEach
    fun setUp() {
        parsing.skipSpaces = false
        parsing.includeRanges = true
    }

    @Test
    fun testAllProperties() = parsing.doTest(true)

    @Test
    fun testBadDefinitions() = parsing.doTest(true)
}