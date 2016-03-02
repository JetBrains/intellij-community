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
    assertEquals(CHECKSUMS.README_TXT, digester.digestRegularFile(new File(getDataDir(), "Readme.txt"), false));
    assertEquals(CHECKSUMS.FOCUSKILLER_DLL, digester.digestRegularFile(new File(getDataDir(), "/bin/focuskiller.dll"), false));
    assertEquals(CHECKSUMS.BOOTSTRAP_JAR, digester.digestZipFile(new File(getDataDir(), "/lib/bootstrap.jar")));
    assertEquals(CHECKSUMS.BOOTSTRAP_JAR_BINARY, digester.digestRegularFile(new File(getDataDir(), "/lib/bootstrap.jar"), false));

    assertEquals(CHECKSUMS.ANNOTATIONS_JAR_NORM,
                 digester.digestRegularFile(new File(getDataDir(), "/lib/annotations.jar"), true));
    assertEquals(CHECKSUMS.ANNOTATIONS_CHANGED_JAR_NORM,
                 digester.digestRegularFile(new File(getDataDir(), "/lib/annotations_changed.jar"), true));
    assertEquals(CHECKSUMS.BOOT_JAR_NORM,
                 digester.digestRegularFile(new File(getDataDir(), "/lib/boot.jar"), true));
    assertEquals(CHECKSUMS.BOOT2_JAR_NORM,
                 digester.digestRegularFile(new File(getDataDir(), "/lib/boot2.jar"), true));
    assertEquals(CHECKSUMS.BOOT2_CHANGED_WITH_UNCHANGED_CONTENT_JAR_NORM,
                 digester.digestRegularFile(new File(getDataDir(), "/lib/boot2_changed_with_unchanged_content.jar"), true));
    assertEquals(CHECKSUMS.BOOT_WITH_DIRECTORY_BECOMES_FILE_JAR_NORM,
                 digester.digestRegularFile(new File(getDataDir(), "/lib/boot_with_directory_becomes_file.jar"), true));
    assertEquals(CHECKSUMS.BOOTSTRAP_JAR_NORM,
                 digester.digestRegularFile(new File(getDataDir(), "/lib/bootstrap.jar"), true));
    assertEquals(CHECKSUMS.BOOTSTRAP_DELETED_JAR_NORM,
                 digester.digestRegularFile(new File(getDataDir(), "/lib/bootstrap_deleted.jar"), true));
  }

  @Test
  public void testMD5() throws Exception {
    Digester digester = new Digester("md5");
    assertEquals(MD5CHECKSUMS.README_TXT, digester.digestRegularFile(new File(getDataDir(), "Readme.txt"), false));
    assertEquals(MD5CHECKSUMS.FOCUSKILLER_DLL, digester.digestRegularFile(new File(getDataDir(), "/bin/focuskiller.dll"), false));
    assertEquals(MD5CHECKSUMS.BOOTSTRAP_JAR, digester.digestZipFile(new File(getDataDir(), "/lib/bootstrap.jar")));
    assertEquals(MD5CHECKSUMS.BOOTSTRAP_JAR_BINARY, digester.digestRegularFile(new File(getDataDir(), "/lib/bootstrap.jar"), false));

    assertEquals(MD5CHECKSUMS.ANNOTATIONS_JAR_NORM,
                 digester.digestRegularFile(new File(getDataDir(), "/lib/annotations.jar"), true));
    assertEquals(MD5CHECKSUMS.ANNOTATIONS_CHANGED_JAR_NORM,
                 digester.digestRegularFile(new File(getDataDir(), "/lib/annotations_changed.jar"), true));
    assertEquals(MD5CHECKSUMS.BOOT_JAR_NORM,
                 digester.digestRegularFile(new File(getDataDir(), "/lib/boot.jar"), true));
    assertEquals(MD5CHECKSUMS.BOOT2_JAR_NORM,
                 digester.digestRegularFile(new File(getDataDir(), "/lib/boot2.jar"), true));
    assertEquals(MD5CHECKSUMS.BOOT2_CHANGED_WITH_UNCHANGED_CONTENT_JAR_NORM,
                 digester.digestRegularFile(new File(getDataDir(), "/lib/boot2_changed_with_unchanged_content.jar"), true));
    assertEquals(MD5CHECKSUMS.BOOT_WITH_DIRECTORY_BECOMES_FILE_JAR_NORM,
                 digester.digestRegularFile(new File(getDataDir(), "/lib/boot_with_directory_becomes_file.jar"), true));
    assertEquals(MD5CHECKSUMS.BOOTSTRAP_JAR_NORM,
                 digester.digestRegularFile(new File(getDataDir(), "/lib/bootstrap.jar"), true));
    assertEquals(MD5CHECKSUMS.BOOTSTRAP_DELETED_JAR_NORM,
                 digester.digestRegularFile(new File(getDataDir(), "/lib/bootstrap_deleted.jar"), true));
  }

  @Test
  public void testBadAlgorithm() throws Exception {
    assertTrue(Digester.isValidAlgorithm("md5"));
    assertTrue(Digester.isValidAlgorithm("crc"));
    assertFalse(Digester.isValidAlgorithm("foo"));
  }
}