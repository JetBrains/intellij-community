// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.regexp

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.codeInsight.PythonRegExpHost
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Test

@Layers.Functional
@Subsystems.CodeInsight
@TestFor(issues = ["PY-23742"], classes = [PythonRegexpInjector::class, PythonRegExpHost::class])
class PyMrabRegexpTest : PyCodeInsightTestCase() {
  @Test
  fun `regex accepts the syntax that re rejects`() = test(
    """
    import regex

    regex.compile(r"(?<=a+)b(?<!a|bc)")
    regex.compile(r"\pL\PL\p{^L}\p{Lu}\p{Latin}\p{IsGreek}\p{Script=Latin}")
    regex.compile(r"[[:alpha:]][[:^digit:]][^[:space:]]")
    regex.compile(r"\h\v\R\X")
    regex.compile(r"(?|(a)|(b))\1")
    regex.compile(r"a\Kb\G")
    regex.compile(r"(?<first>a)(?P<second>b)\g<first>\g'second'(?P=first)")
    regex.compile(r"(?P<n>a)(?P>n)(?&n)")
    regex.compile(r"(a)(?1)(?-1)")
    regex.compile(r"(a)\g1\g{1}")
    regex.compile(r"(?(DEFINE)(?<digit>\d))(?&digit)")
    regex.compile(r"(?(?=a)ab|cd)(?<n>a)?(?(n)b|c)(?(1)d|e)")
    regex.compile(r"(?P<n>a)|(?P<n>b)")
    regex.compile(r"\N{SPACE}")
    """,
    *regexPackage,
  )

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON310)
  fun `regex has possessive quantifiers and atomic groups before Python 3_11`() = test(
    """
    import regex

    regex.compile(r"a++b*+c?+(?>d+)")
    """,
    *regexPackage,
  )

  @Test
  fun `regex reports the syntax that it rejects`() = test(
    """
    import regex

    regex.compile(r"(?<n>a)\k<n>")
    #                      ^^^^^ ERROR This named group reference syntax is not supported in this regex dialect
    regex.compile(r"(?'n'a)")
    #               ^^^^^^^ ERROR This named group syntax is not supported in this regex dialect
    regex.compile(r"(?P<n>a)(?P>m)")
    #                           └ ERROR Unresolved named group reference
    regex.compile(r"(?(VERSION>=1.0)a|b)")
    #                 ^^^^^^^^^^^^^^ ERROR This condition is not supported in this regex dialect
    regex.compile(r"\H")
    #               ^^ ERROR Illegal/unsupported escape sequence
    regex.compile(r"\V")
    #               ^^ ERROR Illegal/unsupported escape sequence
    regex.compile(r"\x{41}")
    #               ^^ ERROR Illegal hexadecimal escape sequence
    """,
    *regexPackage,
  )

  @Test
  fun `regex uses the verbose dialect when the flags argument has VERBOSE`() = test(
    """
    import regex

    regex.compile(r"(?<n>a) # (", regex.X)
    regex.compile(r"(?<n>a) # (", flags=regex.IGNORECASE | regex.VERBOSE)
    regex.search(r"(?<n>a) # (", "text", regex.X)
    regex.search(r"(?<n>a) # (", "text", flags=regex.X, timeout=1.0)
    regex.compile(r"(?<n>a) # (")
    #                          └ ERROR ')' expected
    X = "text"
    regex.sub(r"(?<n>a) # (", "replacement", X)
    #                      └ ERROR ')' expected
    """,
    *regexPackage,
  )

  @Test
  fun `regex before release 2025_10_23 keeps its functions in regex_regex`() = test(
    """
    import regex

    regex.compile(r"(?<n>a)\g<n>(")
    #                            └ ERROR ')' expected
    """,
    "regex/__init__.py" to "from regex.regex import *",
    "regex/regex.py" to "def compile(pattern, flags=0, **kwargs): ...",
  )

  @Test
  fun `re keeps its own dialect`() = test(
    """
    import re

    re.compile(r"(?<n>a)")
    #            ^^^^^^^ ERROR This named group syntax is not supported in this regex dialect
    re.compile(r"(?<=a+)b")
    #                 └ ERROR + repetition not allowed inside lookbehind
    re.compile(r"a\Kb")
    #             ^^ ERROR This boundary is not supported in this regex dialect
    re.compile(r"(?P<n>a)|(?P<n>b)")
    #                         └ ERROR Group with name 'n' already defined
    """,
  )

  @Test
  fun `a regexp injected with a comment keeps the re rules`() = test(
    """
    # language=RegExp
    pattern = "(?<n>a)"
    #          ^^^^^^^ ERROR This named group syntax is not supported in this regex dialect
    """,
  )

  private val regexPackage = arrayOf(
    "regex/__init__.py" to "from regex._main import *",
    "regex/_main.py" to """
      VERBOSE = X = 64
      IGNORECASE = I = 2

      def compile(pattern, flags=0, ignore_unused=False, cache_pattern=None, **kwargs): ...

      def search(pattern, string, flags=0, pos=None, endpos=None, partial=False, concurrent=None, timeout=None,
                 ignore_unused=False, **kwargs): ...

      def sub(pattern, repl, string, count=0, flags=0, pos=None, endpos=None, concurrent=None, timeout=None,
              ignore_unused=False, **kwargs): ...
      """,
  )
}
