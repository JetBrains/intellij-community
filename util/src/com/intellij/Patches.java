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
package com.intellij;

import com.intellij.openapi.util.SystemInfo;

public class Patches {
  public static final boolean ALL_FOLDERS_ARE_WRITABLE = SystemInfo.isWindows;

  /**
   * See sun bug parage.
   * When JTable loses focus it cancel cell editing. It should stop cell editing instead.
   * Actually SUN-boys told they have fixed the bug, but they cancel editing instead of stopping it.
   */
  public static final boolean SUN_BUG_ID_4503845 = SystemInfo.JAVA_VERSION.indexOf("1.4.") != -1;

  /**
   * See sun bug parage.
   * MouseListener on JTabbedPane with SCROLL_TAB_LAYOUT policy doesn't get events. In the related bug
   * #4499556 Sun advices to reimplement or hack JTabbedPane as workaround :)
   */
  public static final boolean SUN_BUG_ID_4620537 = SystemInfo.JAVA_VERSION.indexOf("1.4.") != -1;

  /**
   * See sun bug parage.
   * Debugger hangs on any attempt to attach/listen Connector when attach hanged once. 
   */
  public static final boolean SUN_BUG_338675 = true;

  /**
   * See sun bug parade.
   * If you invoke popup menu, then click on a different window (JFrame, JDialog. It doesn't matter),
   * the JPopupMenu in the previous window still has focus, as does the new window.
   * Seems like focus in two locations at the same time.
   *
   * This bug is fixed in JDK1.5
   */
  public static final boolean SUN_BUG_ID_4218084 = true;

  /**
   * JDK 1.3.x and 1.4.x has the following error. When we close dialog and its content pane is being inserted
   * into another dialog and mouse WAS INSIDE of dialog's content pane then the AWT doesn't change
   * some internal references on focused component. It cause crash of dispatching of MOUSE_EXIT
   * event.
   */
  public static final boolean SPECIAL_WINPUT_METHOD_PROCESSING = SystemInfo.JAVA_VERSION.indexOf("1.4.") != -1;

  /** BasicMenuUI$MenuKeyHandler.menuKeyPressed() incorrect for dynamic menus. */
  public static final boolean SUN_BUG_ID_4738042 = SystemInfo.JAVA_VERSION.indexOf("1.4.") != -1 &&
                                                   SystemInfo.JAVA_VERSION.indexOf("1.4.2") == -1;

  /** BasicTreeUI.FocusHandler doesn't preperly repaint JTree on focus changes */
  public static final boolean SUN_BUG_ID_4893787 = true;

  public static final boolean FILE_CHANNEL_TRANSFER_BROKEN = SystemInfo.isLinux && SystemInfo.OS_VERSION.startsWith("2.6");

  private static final boolean BELOW_142DP2 = SystemInfo.isMac &&
                                              (SystemInfo.JAVA_RUNTIME_VERSION.startsWith("1.4.0") ||
                                               SystemInfo.JAVA_RUNTIME_VERSION.startsWith("1.4.1") ||
                                               SystemInfo.JAVA_RUNTIME_VERSION.equals("1.4.2_03-117.1"));
  private static final boolean DP2_OR_DP3 = SystemInfo.isMac && (
                                                                  SystemInfo.JAVA_RUNTIME_VERSION.startsWith("1.4.2_03") ||
                                                                  SystemInfo.JAVA_RUNTIME_VERSION.startsWith("1.4.2_04")
                                                                  );

  /**
   * Every typing produces InputMethodEvent instead of KeyEvent with keyTyped event code. Fixed in JRE higher than 1.4.2_03-117.1
   */
  public static final boolean APPLE_BUG_ID_3337563 = BELOW_142DP2;

  /**
   * A window that receives focus immediately receives focusLost() event and then focusGained() again.
   */
  public static final boolean APPLE_BUG_ID_3716865 = DP2_OR_DP3;

  /**
   * Incorrect repaint of the components wrapped with JScrollPane.
   */
  public static final boolean APPLE_BUG_ID_3716835 = DP2_OR_DP3;

  /**
   * Focus lost immediately after focus gain if second dialog in the sequence
   */
  public static final boolean APPLE_BUG_ID_3758764 = SystemInfo.isMac;

  /**
   * it happened on Mac that some thread did not suspended during VM suspend
   * resiming VM in this case caused com.sun.jdi.InternalException #13
   */
  public static final boolean MAC_RESUME_VM_HACK = SystemInfo.isMac;

  public static final boolean MAC_HIDE_QUIT_HACK = /*SystemInfo.isMac &&*/ "false".equals(System.getProperty("idea.smooth.progress"));
}