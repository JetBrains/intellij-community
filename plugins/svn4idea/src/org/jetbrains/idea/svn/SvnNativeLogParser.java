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

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 11/2/12
 * Time: 4:14 PM
 */
public class SvnNativeLogParser {

  public static final String CALLED = "CALLED ";

  public static NativeLogReader.CallInfo parse(String s) {
    s = s.trim();
    if (! s.startsWith(CALLED)) return null;
    final int idx = s.indexOf('(', CALLED.length());
    if (idx < 0) return null;
    final String classPlusMethod = new String(s.substring(CALLED.length(), idx));
    final int equalsSignIdx = s.lastIndexOf('=');
    if (equalsSignIdx < 0 || equalsSignIdx == (s.length() - 1)) return null;
    final String returnValStr = s.substring(equalsSignIdx + 1).trim();
    final int returnVal;
    try {
      returnVal = Integer.parseInt(returnValStr);
    } catch (NumberFormatException e) {
      return new NativeLogReader.CallInfo(classPlusMethod, returnValStr);
    }
    return new NativeLogReader.CallInfo(classPlusMethod, returnVal);
  }
}
