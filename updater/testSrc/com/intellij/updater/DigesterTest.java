package com.intellij.updater;

import org.junit.Test;

import java.io.File;
import java.security.NoSuchAlgorithmException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class DigesterTest extends UpdaterTestCase {
  @Test
  public void testBasics() throws Exception {
    Digester digester = new Digester(null);
    assertEquals(CHECKSUMS.README_TXT, digester.digestRegularFile(new File(getDataDir(), "Readme.txt")));
    assertEquals(CHECKSUMS.FOCUSKILLER_DLL, digester.digestRegularFile(new File(getDataDir(), "/bin/focuskiller.dll")));
    assertEquals(CHECKSUMS.BOOTSTRAP_JAR, digester.digestZipFile(new File(getDataDir(), "/lib/bootstrap.jar")));
    assertEquals(CHECKSUMS.BOOTSTRAP_JAR_BINARY, digester.digestRegularFile(new File(getDataDir(), "/lib/bootstrap.jar")));
  }

  @Test
  public void testMD5() throws Exception {
    Digester digester = new Digester("md5");
    assertEquals(MD5CHECKSUMS.README_TXT, digester.digestRegularFile(new File(getDataDir(), "Readme.txt")));
    assertEquals(MD5CHECKSUMS.FOCUSKILLER_DLL, digester.digestRegularFile(new File(getDataDir(), "/bin/focuskiller.dll")));
    assertEquals(MD5CHECKSUMS.BOOTSTRAP_JAR, digester.digestZipFile(new File(getDataDir(), "/lib/bootstrap.jar")));
    assertEquals(MD5CHECKSUMS.BOOTSTRAP_JAR_BINARY, digester.digestRegularFile(new File(getDataDir(), "/lib/bootstrap.jar")));
  }

  @Test
  public void testBadAlgorithm() throws Exception {
    assertTrue(Digester.isValidAlgorithm("md5"));
    assertTrue(Digester.isValidAlgorithm("crc"));
    assertFalse(Digester.isValidAlgorithm("foo"));
  }
}