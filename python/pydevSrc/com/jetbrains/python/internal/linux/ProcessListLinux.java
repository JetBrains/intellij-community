/*******************************************************************************
 * Copyright (c) 2000, 2010 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     QNX Software Systems - Initial API and implementation
 *******************************************************************************/
package com.jetbrains.python.internal.linux;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.python.internal.ProcessUtils;
import com.jetbrains.python.internal.PyProcessInfo;
import com.jetbrains.python.internal.IProcessList;
import com.jetbrains.python.internal.PyProcessInfo;

/**
 * Use through PlatformUtils.
 */
public class ProcessListLinux implements IProcessList {

  PyProcessInfo[] empty = new PyProcessInfo[0];

  public ProcessListLinux() {
  }

  /**
   * Insert the method's description here.
   *
   * @see IProcessList#getProcessList
   */
  public PyProcessInfo[] getProcessList() {
    File proc = new File("/proc"); //$NON-NLS-1$
    File[] pidFiles = null;

    // We are only interested in the pid so filter the rest out.
    try {
      FilenameFilter filter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
          boolean isPID = false;
          try {
            Integer.parseInt(name);
            isPID = true;
          }
          catch (NumberFormatException e) {
          }
          return isPID;
        }
      };
      pidFiles = proc.listFiles(filter);
    }
    catch (SecurityException e) {
    }

    PyProcessInfo[] processInfo = empty;
    if (pidFiles != null) {
      processInfo = new PyProcessInfo[pidFiles.length];
      for (int i = 0; i < pidFiles.length; i++) {
        File cmdLine = new File(pidFiles[i], "cmdline"); //$NON-NLS-1$
        String name;
        try {
          name = new String(ProcessUtils.loadFileText(cmdLine, null)).replace('\0', ' ');
        }
        catch (IOException e) {
          name = "";
        }
        if (name.length() == 0) {
          name = "Unknown"; //$NON-NLS-1$
        }
        processInfo[i] = new PyProcessInfo(pidFiles[i].getName(), name);
      }
    }
    else {
      pidFiles = new File[0];
    }
    return processInfo;
  }
}