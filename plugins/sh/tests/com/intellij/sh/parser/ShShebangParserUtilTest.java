// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.parser;

import junit.framework.TestCase;

public class ShShebangParserUtilTest extends TestCase {
  public void testInvalidShebang() {
    assertNull(ShShebangParserUtil.getInterpreter(null));
    assertNull(ShShebangParserUtil.getInterpreter("/usr/bin"));
  }

  public void testShShebangTest() {
    assertEquals("sh", ShShebangParserUtil.getInterpreter("#!/bin/sh"));
    assertEquals("sh", ShShebangParserUtil.getInterpreter("#! /bin/sh   "));
    assertEquals("sh", ShShebangParserUtil.getInterpreter("#!/bin/sh -x"));
    assertEquals("sh", ShShebangParserUtil.getInterpreter("#!/bin/sh -x\n#"));
    assertEquals("sh", ShShebangParserUtil.getInterpreter("#!C:/AppData/Git/usr/bin/sh.exe"));
  }

  public void testEnvShebangTest() {
    assertEquals("sh", ShShebangParserUtil.getInterpreter("#!/usr/bin/env sh\n"));
    assertEquals("sh", ShShebangParserUtil.getInterpreter("#!  /usr/bin/env sh \n"));
  }

  public void testBashShebangTest() {
    assertEquals("sh", ShShebangParserUtil.getInterpreter("#!/bin/sh -eu  \n"));
  }

  public void testAwkShebangTest() {
    assertEquals("awk", ShShebangParserUtil.getInterpreter("#!/usr/bin/awk -f\n# "));
  }
}