package com.intellij.updater;

import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class DigesterTest extends UpdaterTestCase {
  @Test
  public void testBasics() throws Exception {
    Map<String, Long> checkSums = Digester.digestFiles(getDataDir(), Collections.<String>emptyList(), TEST_UI);
    assertEquals(12, checkSums.size());

    assertEquals(CHECKSUMS.README_TXT, (long)checkSums.get("Readme.txt"));
    assertEquals(CHECKSUMS.FOCUSKILLER_DLL, (long)checkSums.get("bin/focuskiller.dll"));
    assertEquals(CHECKSUMS.BOOTSTRAP_JAR, (long)checkSums.get("lib/bootstrap.jar"));
    Runner.initLogger(System.getProperty("java.io.tmpdir"));
  }
}