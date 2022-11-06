// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.parser;

import junit.framework.TestCase;

public class ShShebangParserUtilTest extends TestCase {
  public void testInvalidShebang() {
    assertNull(ShShebangParserUtil.detectInterpreter(null));
    assertNull(ShShebangParserUtil.detectInterpreter("/usr/bin"));
  }

  public void testShShebangTest() {
    assertEquals("sh", ShShebangParserUtil.detectInterpreter("#!/bin/sh"));
    assertEquals("sh", ShShebangParserUtil.detectInterpreter("#! /bin/sh   "));
    assertEquals("sh", ShShebangParserUtil.detectInterpreter("#!/bin/sh -x"));
    assertEquals("sh", ShShebangParserUtil.detectInterpreter("#!/bin/sh -x\n#"));
    assertEquals("sh", ShShebangParserUtil.detectInterpreter("#!C:/AppData/Git/usr/bin/sh.exe"));
  }

  public void testEnvShebangTest() {
    assertEquals("sh", ShShebangParserUtil.detectInterpreter("#!/usr/bin/env sh\n"));
    assertEquals("sh", ShShebangParserUtil.detectInterpreter("#!  /usr/bin/env sh \n"));
  }

  public void testBashShebangTest() {
    assertEquals("sh", ShShebangParserUtil.detectInterpreter("#!/bin/sh -eu  \n"));
  }

  public void testAwkShebangTest() {
    assertEquals("awk", ShShebangParserUtil.detectInterpreter("#!/usr/bin/awk -f\n# "));
  }
}