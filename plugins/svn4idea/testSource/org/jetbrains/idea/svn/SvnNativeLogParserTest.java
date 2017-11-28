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

public class SvnNativeLogParserTest extends TestCase {
  private final static String[] ourCases = {"CALLED ISVNKernel32Library#GetVersionExW(allocated@0x82f4d0 (276 bytes)) = 1",
    "CALLED ISVNWin32Library#SHGetFolderPathW(null, 35, null, 0, [C@30602d) = 0",
    "CALLED ISVNWinCryptLibrary#CryptUnprotectData( flags =, 1) = true",
    "CALLED ISVNKernel32Library#LocalFree(native@0x543fd8d0) = null",
    "CALLED ISVNKernel32Library#LocalFree(native@0x543fd8d0)"};

  private final static NativeLogReader.CallInfo[] ourResults = {
    new NativeLogReader.CallInfo("ISVNKernel32Library#GetVersionExW", 1),
    new NativeLogReader.CallInfo("ISVNWin32Library#SHGetFolderPathW", 0),
    new NativeLogReader.CallInfo("ISVNWinCryptLibrary#CryptUnprotectData", "true"),
    new NativeLogReader.CallInfo("ISVNKernel32Library#LocalFree", "null"),
    null
  };

  public void testWindows() {
    for (int i = 0; i < ourCases.length; i++) {
      final String aCase = ourCases[i];
      final NativeLogReader.CallInfo info = SvnNativeLogParser.parse(aCase);
      Assert.assertEquals(ourResults[i], info);
    }
  }
}
