package com.jetbrains.python;

import junit.framework.TestCase;

/**
 * @author yole
 */
public class PyEncodingTest extends TestCase {
  public void testEncodingEmacs() {
    doTest("#!/usr/bin/python\n# -*- coding: iso-8859-15 -*-\nimport os, sys", "iso-8859-15");
  }

  public void testEncodingPlain() {
    doTest("# This Python file uses the following encoding: utf-8\nimport os, sys", "utf-8");
  }

  public void testEncodingLatin1() {
    doTest("#!/usr/local/bin/python\n# coding: latin-1\nimport os, sys", "iso-8859-1");
  }

  private static void doTest(final String text, final String expected) {
    assertEquals(expected, PythonFileType.getCharsetFromEncodingDeclaration(text));
  }
}
