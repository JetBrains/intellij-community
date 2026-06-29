// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyTestCase

class PyTypedDictKeywordArgumentCompletionTest : PyTestCase() {

  @TestFor(issues = ["PY-90617"])
  fun testOffersClassParameters() {
    val variants = complete("from typing import TypedDict\nclass Movie(TypedDict, <caret>)")
    assertContainsElements(variants, "total=", "closed=", "extra_items=")
  }

  @TestFor(issues = ["PY-90617"])
  fun testExcludesAlreadyPresentParameter() {
    val variants = complete("from typing import TypedDict\nclass Movie(TypedDict, closed=True, <caret>)")
    assertContainsElements(variants, "total=", "extra_items=")
    assertDoesntContain(variants, "closed=")
  }

  @TestFor(issues = ["PY-90617"])
  fun testNotOfferedForNonTypedDictClass() {
    val variants = complete("class Movie(object, <caret>)")
    assertDoesntContain(variants, "total=", "closed=", "extra_items=")
  }

  private fun complete(source: String): List<String> {
    myFixture.configureByText("a.py", source)
    myFixture.completeBasic()
    return myFixture.lookupElementStrings ?: emptyList()
  }
}
