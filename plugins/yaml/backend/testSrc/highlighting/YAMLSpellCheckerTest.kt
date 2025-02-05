// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.highlighting

import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.InjectionTestFixture
import org.intellij.lang.regexp.RegExpLanguage

class YAMLSpellCheckerTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()

    myFixture.enableInspections(SpellCheckingInspection::class.java)
  }

  fun testSpellChecking() {
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

  fun testHashesQuotedSpelling() {
    myFixture.configureByText("hashes.yaml", """
      data:
        typo: '<TYPO>hereistheerror</TYPO>'
        uuid: 'f19c4bd2-4c11-4725-a613-06aaead4325e'
        md5: '79054025255fb1a26e4bc422adfebeed'
        sha1: "c3499c2729730aaff07efb8676a92dcb6f8a3f8f"
        sha256: "50d858e0985ecc7f60418aaf0cc5ab587f42c2570a884095a9e8ccacd0f6545c"
        jwt: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.dyt0CoTl4WoVjAHI9Q_CwSKhl6d_9rhM3NrXuJttkao'
    """.trimIndent())

    myFixture.checkHighlighting(true, false, true)
  }

  fun testHashesUnquotedSpelling() {
    myFixture.configureByText("hashes.yaml", """
      data:
        typo: <TYPO>hereistheerror</TYPO>
        uuid: f19c4bd2-4c11-4725-a613-06aaead4325e
        md5: 79054025255fb1a26e4bc422adfebeed
        sha1: c3499c2729730aaff07efb8676a92dcb6f8a3f8f
        sha256: 50d858e0985ecc7f60418aaf0cc5ab587f42c2570a884095a9e8ccacd0f6545c
        jwt: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.dyt0CoTl4WoVjAHI9Q_CwSKhl6d_9rhM3NrXuJttkao
    """.trimIndent())

    myFixture.checkHighlighting(true, false, true)
  }

  fun testInjectedFragments() {
    myFixture.configureByText("hashes.yaml", """
      data:
        # language=RegExp
        ok: '[i<caret>like]?'
    """.trimIndent())

    InjectionTestFixture(myFixture)
      .assertInjectedLangAtCaret(RegExpLanguage.INSTANCE.id)

    myFixture.checkHighlighting(true, false, true)
  }
}