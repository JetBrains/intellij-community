// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.command.WriteCommandAction
import com.jetbrains.python.fixtures.PyTestCase

class PyClassFormCompletionTest : PyTestCase() {

  fun testDataclass() {
    val text = doComplete("data<caret>", "dataclass")
    assertTrue(text, text.contains("from dataclasses import dataclass"))
    assertTrue(text, text.contains("@dataclass"))
    assertTrue(text, text.contains("class"))
    // The import must precede the scaffold that uses it. Regression guard: when completing at the very start of a
    // file the scaffold used to be inserted above its own freshly added import.
    assertTrue(text, text.indexOf("from dataclasses import dataclass") < text.indexOf("@dataclass"))
  }

  fun testEnum() {
    val text = doComplete("Enu<caret>", "Enum")
    assertTrue(text, text.contains("from enum import Enum"))
    assertTrue(text, text.contains("(Enum):"))
  }

  fun testTypedDict() {
    val text = doComplete("TypedDi<caret>", "TypedDict")
    assertTrue(text, text.contains("from typing import TypedDict"))
    assertTrue(text, text.contains("(TypedDict):"))
  }

  fun testNamedTuple() {
    val text = doComplete("NamedTup<caret>", "NamedTuple")
    assertTrue(text, text.contains("from typing import NamedTuple"))
    assertTrue(text, text.contains("(NamedTuple):"))
  }

  fun testProtocol() {
    val text = doComplete("Proto<caret>", "Protocol")
    assertTrue(text, text.contains("from typing import Protocol"))
    assertTrue(text, text.contains("(Protocol):"))
  }

  fun testCaseInsensitivePrefix() {
    val text = doComplete("typeddict<caret>", "TypedDict")
    assertTrue(text, text.contains("from typing import TypedDict"))
    assertTrue(text, text.contains("(TypedDict):"))
  }

  fun testNotOfferedForQualifiedReference() {
    myFixture.configureByText("a.py", "import foo\nfoo.dat<caret>")
    myFixture.completeBasic()
    assertDoesntContain(myFixture.lookupElementStrings ?: emptyList(), "dataclass")
  }

  private fun doComplete(source: String, variant: String): String {
    TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    myFixture.configureByText("a.py", source)
    val elements = myFixture.completeBasic()
    val lookup = myFixture.lookup
    if (lookup != null && elements != null && elements.size > 1) {
      // Pick our scaffolding element: its object is the lookup string itself, unlike symbol completions.
      lookup.currentItem = elements.first { it.lookupString == variant && it.`object` == variant }
      myFixture.type('\n')
    }
    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      TemplateManagerImpl.getTemplateState(myFixture.editor)?.gotoEnd(false)
    }
    return myFixture.file.text
  }
}
