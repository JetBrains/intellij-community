// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.parser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ShShebangParserUtilTest {

  @Test
  public void invalidShebangTest() {
    assertNull(ShShebangParserUtil.getInterpreter(null));
    assertNull(ShShebangParserUtil.getInterpreter("/usr/bin"));
  }

  @Test
  public void shShebangTest() {
    assertEquals("sh", ShShebangParserUtil.getInterpreter("#!/bin/sh"));
    assertEquals("sh", ShShebangParserUtil.getInterpreter("#! /bin/sh   "));
    assertEquals("sh", ShShebangParserUtil.getInterpreter("#!/bin/sh -x"));
    assertEquals("sh", ShShebangParserUtil.getInterpreter("#!/bin/sh -x\n#"));
    assertEquals("sh", ShShebangParserUtil.getInterpreter("#!C:/AppData/Git/usr/bin/sh.exe"));
  }

  @Test
  public void envShebangTest() {
    assertEquals("sh", ShShebangParserUtil.getInterpreter("#!/usr/bin/env sh\n"));
    assertEquals("sh", ShShebangParserUtil.getInterpreter("#!  /usr/bin/env sh \n"));
  }

  @Test
  public void bashShebangTest() {
    assertEquals("sh", ShShebangParserUtil.getInterpreter("#!/bin/sh -eu  \n"));
  }

  @Test
  public void awkShebangTest() {
    assertEquals("awk", ShShebangParserUtil.getInterpreter("#!/usr/bin/awk -f\n# "));
  }
}