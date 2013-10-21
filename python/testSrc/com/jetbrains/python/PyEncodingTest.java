/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
