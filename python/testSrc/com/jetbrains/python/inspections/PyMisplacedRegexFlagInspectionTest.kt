// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

@Subsystems.Inspections
@Layers.Functional
@TestFor(issues = ["PY-40518"], classes = [PyMisplacedRegexFlagInspection::class])
@PyCodeInsightTestCase.TestInspections(enableInspections = [PyMisplacedRegexFlagInspection::class])
class PyMisplacedRegexFlagInspectionTest : PyCodeInsightTestCase() {
  @Test
  fun `flag passed as a position of a pattern method`() = test("""
    import re

    regex = re.compile(r'\wo\w')
    regex.search('foo bar', re.I)
    #                       ^^^^ WARNING A regular expression flag is passed as the argument 'pos'. Pass the flag to 're.compile()'
    regex.match('foo bar', 0, re.IGNORECASE | re.MULTILINE)
    #                         ^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING A regular expression flag is passed as the argument 'endpos'. Pass the flag to 're.compile()'
    regex.finditer('foo bar', endpos=re.M)
    #                                ^^^^ WARNING A regular expression flag is passed as the argument 'endpos'. Pass the flag to 're.compile()'
    flags = re.I
    re.compile(rb'o').fullmatch(b'foo', flags)
    #                                   ^^^^^ WARNING A regular expression flag is passed as the argument 'pos'. Pass the flag to 're.compile()'
    """.trimIndent())

  @Test
  fun `flag passed as a count of split or sub`() = test("""
    import re

    re.sub(r'o', '0', 'Foo', re.I)
    #                        ^^^^ WARNING A regular expression flag is passed as the argument 'count'. Pass the flag as the keyword argument 'flags'
    re.split(r'o', 'Foo', re.I)
    #                     ^^^^ WARNING A regular expression flag is passed as the argument 'maxsplit'. Pass the flag as the keyword argument 'flags'
    re.compile(r'o').subn('0', 'Foo', re.I)
    #                                 ^^^^ WARNING A regular expression flag is passed as the argument 'count'. Pass the flag to 're.compile()'
    """.trimIndent())

  @Test
  fun `flag in its place or a number in a position`() = test("""
    import re

    re.search(r'\wo\w', 'foo bar', re.I)
    re.sub(r'o', '0', 'Foo', flags=re.I)
    regex = re.compile(r'\wo\w', re.I)
    start = 1
    regex.search('foo bar', start)
    regex.fullmatch('foo bar', 0, len('foo'))

    class Finder:
        def search(self, text, flags): ...

    Finder().search('foo bar', re.I)
    """.trimIndent())
}
