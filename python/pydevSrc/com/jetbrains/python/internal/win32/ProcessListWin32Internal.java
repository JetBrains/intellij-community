/*******************************************************************************
 * Copyright (c) 2000, 2014 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     QNX Software Systems - Initial API and implementation
 *     Martin Oberhuber (Wind River) - [303083] Split out the Spawner
 *******************************************************************************/
package com.jetbrains.python.internal.win32;

import com.google.common.collect.Lists;
import com.jetbrains.python.internal.PyProcessInfo;
import com.jetbrains.python.internal.IProcessList;
import com.jetbrains.python.internal.PyProcessInfo;
import com.jetbrains.python.internal.ProcessUtils;

import java.io.*;
import java.util.List;

/*
 * This implementation uses a listtasks which is shipped together (so, it should always work on windows).
 * 
 * Use through PlatformUtils.
 */
public class ProcessListWin32Internal implements IProcessList {

  private PyProcessInfo[] NOPROCESS = new PyProcessInfo[0];
  private String myHelpersRoot;

  public ProcessListWin32Internal(String helpersRoot) {

    myHelpersRoot = helpersRoot;
  }

  public PyProcessInfo[] getProcessList() {
    Process p = null;
    String command = null;
    InputStream in = null;
    PyProcessInfo[] procInfos = NOPROCESS;

    try {
      File file = new File(myHelpersRoot, "process/listtasks.exe");


      //TODO: use listtasks.exe
      //IPath relative = new Path("com/jetbrains/python/internal/win32").addTrailingSeparator().append("listtasks.exe");
      //file = BundleUtils.getRelative(relative, bundle);

      if (file != null && file.exists()) {
        command = file.getCanonicalPath();
        if (command != null) {
          try {
            p = ProcessUtils.createProcess(new String[]{command}, null, null);
            in = p.getInputStream();
            InputStreamReader reader = new InputStreamReader(in);
            procInfos = parseListTasks(reader);
          }
          finally {
            if (in != null) {
              in.close();
            }
            if (p != null) {
              p.destroy();
            }
          }
        }
      }
    }
    catch (IOException e) {
    }
    return procInfos;
  }

  public PyProcessInfo[] parseListTasks(InputStreamReader reader) {
    BufferedReader br = new BufferedReader(reader);
    List<PyProcessInfo> processList = Lists.newArrayList();
    try {
      String line;
      while ((line = br.readLine()) != null) {
        int tab = line.indexOf('\t');
        if (tab != -1) {
          String proc = line.substring(0, tab).trim();
          String name = line.substring(tab).trim();
          if (proc.length() > 0 && name.length() > 0) {
            try {
              int pid = Integer.parseInt(proc);
              processList.add(new PyProcessInfo(pid, name));
            }
            catch (NumberFormatException e) {
            }
          }
        }
      }
    }
    catch (IOException e) {
    }
    return processList.toArray(new PyProcessInfo[processList.size()]);
  }
}