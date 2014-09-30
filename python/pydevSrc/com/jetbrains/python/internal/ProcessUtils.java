/******************************************************************************
 * Copyright (C) 2013  Fabio Zadrozny
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Fabio Zadrozny <fabiofz@gmail.com> - initial API and implementation
 ******************************************************************************/
package com.jetbrains.python.internal;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.jetbrains.python.internal.linux.ProcessListLinux;
import com.jetbrains.python.internal.macos.ProcessListMac;
import com.jetbrains.python.internal.win32.ProcessListWin32;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class ProcessUtils {
  private static final Logger LOG = Logger.getInstance(ProcessUtils.class);

  /**
   * Passes the commands directly to Runtime.exec (with the passed envp)
   */
  public static Process createProcess(String[] cmdarray, String[] envp, File workingDir) throws IOException {
    return Runtime.getRuntime().exec(getWithoutEmptyParams(cmdarray), getWithoutEmptyParams(envp), workingDir);
  }

  /**
   * @return a new array without any null/empty elements originally contained in the array.
   */
  private static String[] getWithoutEmptyParams(String[] cmdarray) {
    if (cmdarray == null) {
      return null;
    }
    ArrayList<String> list = new ArrayList<String>();
    for (String string : cmdarray) {
      if (string != null && string.length() > 0) {
        list.add(string);
      }
    }
    return list.toArray(new String[list.size()]);
  }


  public static IProcessList getProcessList(String helpersRoot) {
    if (SystemInfo.isWindows) {
      return new ProcessListWin32(helpersRoot);
    }
    if (SystemInfo.isLinux) {
      return new ProcessListLinux();
    }
    if (SystemInfo.isMac) {
      return new ProcessListMac();
    }

    LOG.error("Unexpected platform. Unable to list processes.");
    return new IProcessList() {

      @Override
      public PyProcessInfo[] getProcessList() {
        return new PyProcessInfo[0];
      }
    };
  }
}
