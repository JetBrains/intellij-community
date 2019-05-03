package com.intellij.bash.parser;

import org.junit.Test;

import static org.junit.Assert.*;

public class BashShebangParserUtilTest {

  @Test
  public void invalidShebangTest() {
    assertNull(BashShebangParserUtil.getInterpreter(null));
    assertNull(BashShebangParserUtil.getInterpreter("/usr/bin"));
  }

  @Test
  public void shShebangTest() {
    assertEquals("sh", BashShebangParserUtil.getInterpreter("#!/bin/sh"));
    assertEquals("sh", BashShebangParserUtil.getInterpreter("#! /bin/sh   "));
    assertEquals("sh", BashShebangParserUtil.getInterpreter("#!/bin/sh -x"));
    assertEquals("sh", BashShebangParserUtil.getInterpreter("#!/bin/sh -x\n#"));
    assertEquals("sh", BashShebangParserUtil.getInterpreter("#!C:/AppData/Git/usr/bin/sh.exe"));
  }

  @Test
  public void envShebangTest() {
    assertEquals("bash", BashShebangParserUtil.getInterpreter("#!/usr/bin/env bash\n"));
    assertEquals("bash", BashShebangParserUtil.getInterpreter("#!  /usr/bin/env bash \n"));
  }

  @Test
  public void bashShebangTest() {
    assertEquals("bash", BashShebangParserUtil.getInterpreter("#!/bin/bash -eu  \n"));
  }

  @Test
  public void awkShebangTest() {
    assertEquals("awk", BashShebangParserUtil.getInterpreter("#!/usr/bin/awk -f\n# "));
  }
}