/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
    assertEquals(CHECKSUMS.FOCUS_KILLER_DLL, digester.digestRegularFile(new File(getDataDir(), "/bin/focuskiller.dll")));
    assertEquals(CHECKSUMS.BOOTSTRAP_JAR, digester.digestZipFile(new File(getDataDir(), "/lib/bootstrap.jar")));
    assertEquals(CHECKSUMS.BOOTSTRAP_JAR_BINARY, digester.digestRegularFile(new File(getDataDir(), "/lib/bootstrap.jar")));
  }

  @Test
  public void testMD5() throws Exception {
    Digester digester = new Digester("md5");
    assertEquals(MD5CHECKSUMS.README_TXT, digester.digestRegularFile(new File(getDataDir(), "Readme.txt")));
    assertEquals(MD5CHECKSUMS.FOCUS_KILLER_DLL, digester.digestRegularFile(new File(getDataDir(), "/bin/focuskiller.dll")));
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