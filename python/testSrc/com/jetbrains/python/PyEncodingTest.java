// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyTestCase;


public class PyEncodingTest extends PyTestCase {
  public void testEncodingEmacs() {
    doTest("#!/usr/bin/python\n# -*- coding: iso-8859-15 -*-\nimport os, sys", "iso-8859-15");
  }

  public void testEncodingPlain() {
    doTest("# This Python file uses the following encoding: utf-8\nimport os, sys", "utf-8");
  }

  public void testEncodingLatin1() {
    doTest("#!/usr/local/bin/python\n# coding: latin-1\nimport os, sys", "iso-8859-1");
  }

  private void doTest(final String text, final String expected) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    assertEquals(expected, PythonFileType.getCharsetFromEncodingDeclaration(myFixture.getFile()));
  }
}
