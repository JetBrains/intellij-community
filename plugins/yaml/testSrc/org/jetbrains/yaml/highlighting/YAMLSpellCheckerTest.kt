// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.highlighting

import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class YAMLSpellCheckerTest : BasePlatformTestCase() {
  fun testSpellChecking() {
    myFixture.enableInspections(SpellCheckingInspection::class.java)
    myFixture.configureByText("test.yaml", """
      a: b
      # hello <TYPO descr="Typo: In word 'warld'">warld</TYPO>
      key1: >
        hello <TYPO descr="Typo: In word 'warld'">warld</TYPO>
      key2: |
        hello <TYPO descr="Typo: In word 'warld'">warld</TYPO>
      key3: |
        "hello <TYPO descr="Typo: In word 'warld'">warld</TYPO>"
      key4: |
        'hello <TYPO descr="Typo: In word 'warld'">warld</TYPO>'
      'hello <TYPO descr="Typo: In word 'warld'">warld</TYPO>': |
        value 1
      "just <TYPO descr="Typo: In word 'warld'">warld</TYPO>": |
        value 2
      <TYPO descr="Typo: In word 'warld'">warld</TYPO>: value3
    """.trimIndent())
    myFixture.checkHighlighting(true, false, true)
  }
}