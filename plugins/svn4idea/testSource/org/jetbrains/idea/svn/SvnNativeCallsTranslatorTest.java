/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import junit.framework.Assert;
import junit.framework.TestCase;
import org.tmatesoft.svn.core.internal.util.jna.ISVNGnomeKeyringLibrary;

public class SvnNativeCallsTranslatorTest extends TestCase {
  public void testMac() {
    assertResult(SvnNativeCallsTranslator.forMac(new NativeLogReader.CallInfo("Test", -25293)), false);
    assertResult(SvnNativeCallsTranslator.forMac(new NativeLogReader.CallInfo("Test", 0)), null);
    assertResult(SvnNativeCallsTranslator.forMac(new NativeLogReader.CallInfo("Test", -67249)), false);
    assertResult(SvnNativeCallsTranslator.forMac(new NativeLogReader.CallInfo("Test", -67606)), false);
  }

  private void assertResult(final String result, final Boolean isNull) {
    if (isNull == null) {
      Assert.assertNull(result);
    } else {
      System.out.println();
      System.out.println(result);
      Assert.assertNotNull(result);
    }
  }

  public void testWindows() {
    assertResult(SvnNativeCallsTranslator.forWindows(new NativeLogReader.CallInfo("ISVNKernel32Library#GetVersionExW", 1)), null);
    assertResult(SvnNativeCallsTranslator.forWindows(new NativeLogReader.CallInfo("ISVNWinCryptLibrary#CryptUnprotectData", "true")), null);
    assertResult(SvnNativeCallsTranslator.forWindows(new NativeLogReader.CallInfo("ISVNWinCryptLibrary#CryptUnprotectData", "false")), false);
    assertResult(SvnNativeCallsTranslator.forWindows(new NativeLogReader.CallInfo("ISVNSecurityLibrary#InitializeSecurityContextW", 1)), false);
    assertResult(SvnNativeCallsTranslator.forWindows(new NativeLogReader.CallInfo("ISVNSecurityLibrary#InitializeSecurityContextW", 0)), null);
  }

  public void testLinux() {
    assertResult(SvnNativeCallsTranslator.forLinux(new NativeLogReader.CallInfo("ISVNGnomeKeyringLibrary#gnome_keyring_is_available", "true")), null);
    assertResult(SvnNativeCallsTranslator.forLinux(new NativeLogReader.CallInfo("ISVNGnomeKeyringLibrary#gnome_keyring_is_available", "false")), false);
    assertResult(SvnNativeCallsTranslator.forLinux(new NativeLogReader.CallInfo("ISVNGnomeKeyringLibrary#gnome_keyring_find_network_password_sync",
                                                                                ISVNGnomeKeyringLibrary.GNOME_KEYRING_RESULT_BAD_ARGUMENTS)), false);
    assertResult(SvnNativeCallsTranslator.forLinux(new NativeLogReader.CallInfo("ISVNGnomeKeyringLibrary#gnome_keyring_find_network_password_sync",
                                                                                ISVNGnomeKeyringLibrary.GNOME_KEYRING_RESULT_OK)), null);
    assertResult(SvnNativeCallsTranslator.forLinux(new NativeLogReader.CallInfo("SomeOther#method", ISVNGnomeKeyringLibrary.GNOME_KEYRING_RESULT_NO_MATCH)), null);

  }
}
