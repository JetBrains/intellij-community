/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.util;

public class SystemInfo {
  private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
  public static final String OS_VERSION = System.getProperty("os.version").toLowerCase();
  public static final String JAVA_VERSION = System.getProperty("java.version");
  public static final String JAVA_RUNTIME_VERSION = System.getProperty("java.runtime.version");

  public static final boolean isWindows = OS_NAME.startsWith("windows");
  public static final boolean isWindowsNT = OS_NAME.startsWith("windows nt");
  public static final boolean isWindows2000 = OS_NAME.startsWith("windows 2000");
  public static final boolean isWindows2003 = OS_NAME.startsWith("windows 2003");
  public static final boolean isWindowsXP = OS_NAME.startsWith("windows xp");
  public static final boolean isWindows9x = OS_NAME.startsWith("windows 9") || OS_NAME.startsWith("windows me");
  public static final boolean isOS2 = OS_NAME.startsWith("os/2") || OS_NAME.startsWith("os2");
  public static final boolean isMac = OS_NAME.startsWith("mac");
  public static final boolean isFreeBSD = OS_NAME.startsWith("freebsd");
  public static final boolean isLinux = OS_NAME.startsWith("linux");
  public static final boolean isUnix = !isWindows && !isOS2;

  public static final boolean isMacSystemMenu = isMac && "true".equals(System.getProperty("apple.laf.useScreenMenuBar"));

  public static final boolean isFileSystemCaseSensitive = !isWindows && !isOS2;
}
