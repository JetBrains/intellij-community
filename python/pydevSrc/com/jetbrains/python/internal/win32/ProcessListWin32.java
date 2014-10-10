/*******************************************************************************
 * Copyright (c) 2014 Brainwy Software Ltda.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Fabio Zadrozny
 *******************************************************************************/
package com.jetbrains.python.internal.win32;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Joiner;
import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.python.internal.PyProcessInfo;
import com.jetbrains.python.internal.IProcessList;
import com.jetbrains.python.internal.PyProcessInfo;
import com.jetbrains.python.internal.ProcessUtils;

/*
 * This implementation uses the tasklist.exe from windows (must be on the path).
 * 
 * Use through PlatformUtils.
 */
public class ProcessListWin32 implements IProcessList {
  private static final Logger LOG = Logger.getInstance(ProcessListWin32.class);
  private String myHelpersRoot;

  public ProcessListWin32(String helpersRoot) {

    myHelpersRoot = helpersRoot;
  }

  public PyProcessInfo[] getProcessList() {

    try {
      return createFromWMIC();
    }
    catch (Exception e) {
      //Keep on going for other alternatives
    }

    Process p = null;
    InputStream in = null;
    PyProcessInfo[] procInfos = new PyProcessInfo[0];

    try {

      try {
        try {
          p = ProcessUtils.createProcess(new String[]{"tasklist.exe", "/fo", "csv", "/nh", "/v"}, null,
                                         null);
        }
        catch (Exception e) {
          //Use fallback
          return new ProcessListWin32Internal(myHelpersRoot).getProcessList();
        }
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
    catch (IOException e) {
    }
    return procInfos;
  }

  private PyProcessInfo[] createFromWMIC() throws Exception {
    Process p = ProcessUtils.createProcess(new String[]{"wmic.exe", "path", "win32_process", "get",
                                             "Caption,Processid,Commandline"}, null,
                                           null);
    List<PyProcessInfo> lst = new ArrayList<PyProcessInfo>();
    InputStream in = p.getInputStream();
    InputStreamReader reader = new InputStreamReader(in);
    try {
      BufferedReader br = new BufferedReader(reader);
      String line = br.readLine();
      //We should have something as: Caption      CommandLine      ProcessId
      //From this we get the number of characters for each column
      int commandLineI = line.indexOf("CommandLine");
      int processIdI = line.indexOf("ProcessId");
      if (commandLineI == -1) {
        throw new AssertionError("Could not find CommandLine in: " + line);
      }
      if (processIdI == -1) {
        throw new AssertionError("Could not find ProcessId in: " + line);
      }

      while (true) {
        line = br.readLine();
        if (line == null) {
          break;
        }
        if (line.trim().length() == 0) {
          continue;
        }
        String name = line.substring(0, commandLineI).trim();
        String commandLine = line.substring(commandLineI, processIdI).trim();
        String processId = line.substring(processIdI, line.length()).trim();
        lst.add(new PyProcessInfo(Integer.parseInt(processId), name + "   " + commandLine));
      }
      if (lst.size() == 0) {
        throw new AssertionError("Error: no processes found");
      }
      return lst.toArray(new PyProcessInfo[0]);
    }
    catch (Exception e) {
      LOG.error(e);
      throw e;
    }
    finally {
      in.close();
    }
  }

  public PyProcessInfo[] parseListTasks(InputStreamReader reader) {
    BufferedReader br = new BufferedReader(reader);
    CSVReader csvReader = new CSVReader(br);
    List<PyProcessInfo> processList = new ArrayList();
    String[] next;
    do {
      try {
        next = csvReader.readNext();
        if (next != null) {
          int pid = Integer.parseInt(next[1]);
          String name = Joiner.on(" - ").join(next[0], next[next.length - 1]);
          processList.add(new PyProcessInfo(pid, name));
        }
      }
      catch (IOException e) {
        break;
      }
    }
    while (next != null);

    return processList.toArray(new PyProcessInfo[processList.size()]);
  }
}