/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.updater;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.StringArray;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.win32.StdCallLibrary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.intellij.updater.Runner.LOG;
import static com.sun.jna.platform.win32.WinError.ERROR_MORE_DATA;

/**
 * <p>A utility class to find processes that hold a lock to a file. This relies on a Windows API called
 * <a href="http://msdn.microsoft.com/en-us/library/windows/desktop/cc948910(v=vs.85).aspx">RestartManager</a>.</p>
 *
 * <p>This class uses the RestartManager and the Kernel32 APIs, and it tries to initialize them the first
 * time it is run. If the RestartManager DLL is not found, it being because we are running on XP or
 * because we are not running on Windows, then the class is flagged as failed and no further attempts
 * will be made to load the DLL.</p>
 */
@SuppressWarnings("IO_FILE_USAGE")
public final class NativeFileManager {
  private static final int MAX_PROCESSES = 10;
  private static final int MAX_RETRIES = 5;

  private static boolean ourFailed = !Utils.IS_WINDOWS;

  public static final class Process {
    public final int pid;
    public final String name;

    public Process(int pid, String name) {
      this.pid = pid;
      this.name = name;
    }

    public boolean terminate() {
      Kernel32.HANDLE process = Kernel32.INSTANCE.OpenProcess(WinNT.PROCESS_TERMINATE | WinNT.SYNCHRONIZE, false, pid);
      if (process == null || process.getPointer() == null) {
        LOG.warning("Unable to find process " + name + '[' + pid + ']');
        return false;
      }
      else {
        Kernel32.INSTANCE.TerminateProcess(process, 1);
        int wait = Kernel32.INSTANCE.WaitForSingleObject(process, 1000);
        if (wait != WinBase.WAIT_OBJECT_0) {
          LOG.warning("Timed out while waiting for process " + name + '[' + pid + "] to end");
          return false;
        }
        Kernel32.INSTANCE.CloseHandle(process);
        return true;
      }
    }
  }

  public static boolean isAvailable() {
    return !ourFailed;
  }

  @VisibleForTesting
  public static @NotNull List<Process> getProcessesUsing(File file, int initialBufferSize) {
    return queryProcesses(List.of(file), initialBufferSize);
  }

  private static @NotNull List<Process> queryProcesses(List<File> files, int initialBufferSize) {
    // If the DLL was not present (XP or other OS), do not try to find it again.
    if (!ourFailed) {
      try {
        IntByReference session = new IntByReference();
        char[] sessionKey = new char[Win32RestartManager.CCH_RM_SESSION_KEY + 1];
        int error = Win32RestartManager.INSTANCE.RmStartSession(session, 0, sessionKey);
        if (error != 0) {
          LOG.warning("RmStartSession(): " + error);
        }
        else {
          try {
            WString[] names = new WString[files.size()];
            for (int i = 0; i < names.length; i++) {
              names[i] = new WString(files.get(i).toString());
            }

            StringArray resources = new StringArray(names);
            error = Win32RestartManager.INSTANCE.RmRegisterResources(session.getValue(), names.length, resources, 0, Pointer.NULL, 0, null);

            if (error != 0) {
              LOG.warning("RmRegisterResources(" + describe(files) + "): " + error);
            }
            else {
              int arraySize = initialBufferSize;
              for (int retry = 0; retry < MAX_RETRIES; ++retry) {
                IntByReference procInfoNeeded = new IntByReference();
                Win32RestartManager.RmProcessInfo info = new Win32RestartManager.RmProcessInfo();
                Win32RestartManager.RmProcessInfo[] infos = (Win32RestartManager.RmProcessInfo[])info.toArray(arraySize);
                IntByReference procInfo = new IntByReference(infos.length);
                error = Win32RestartManager.INSTANCE.RmGetList(session.getValue(), procInfoNeeded, procInfo, info, new LongByReference());
                if (error == ERROR_MORE_DATA) {
                  arraySize = procInfoNeeded.getValue();
                  continue;
                }

                if (error != 0) {
                  LOG.warning("RmGetList(" + describe(files) + "): " + error);
                }
                else {
                  int n = procInfo.getValue();
                  List<Process> processes = new ArrayList<>(n);
                  for (int i = 0; i < n; i++) {
                    processes.add(new Process(infos[i].Process.dwProcessId, new String(infos[i].strAppName).trim()));
                  }
                  return processes;
                }
              }
            }
          }
          finally {
            Win32RestartManager.INSTANCE.RmEndSession(session.getValue());
          }
        }
      }
      catch (Throwable t) {
        // Best effort approach, if no DLL is found ignore.
        ourFailed = true;
        LOG.log(Level.WARNING, "Missing or dysfunctional restart manager DLL", t);
      }
    }

    return List.of();
  }

  public static @NotNull List<Process> getProcessesUsing(File file) {
    return getProcessesUsing(file, MAX_PROCESSES);
  }

  public static Set<File> findUnusedFiles(@NotNull Collection<File> files, @NotNull UpdaterUI ui) throws OperationCancelledException {
    if (ourFailed || files.isEmpty()) return Set.of();
    return findUnusedFiles(files, ui, group -> queryProcesses(group, MAX_PROCESSES));
  }

  @VisibleForTesting
  static @NotNull Set<File> findUnusedFiles(@NotNull Collection<File> files,
                                            @NotNull UpdaterUI ui,
                                            @NotNull Function<List<File>,
                                            @NotNull List<Process>> query)
    throws OperationCancelledException {
    var unused = new HashSet<File>();
    if (!files.isEmpty()) {
      collectUnusedFiles(new ArrayList<>(files), unused, ui, query);
    }
    ui.checkCancelled();
    LOG.info("Restart Manager: " + unused.size() + " of " + files.size() + " files are free");
    return unused;
  }

  private static @NotNull String describe(@NotNull List<File> files) {
    return files.stream().map(File::getAbsolutePath).collect(Collectors.joining(", "));
  }

  private static void collectUnusedFiles(@NotNull List<File> files,
                                         @NotNull Set<File> unused,
                                         @NotNull UpdaterUI ui,
                                         @NotNull Function<List<File>,
                                         @NotNull List<Process>> query) throws OperationCancelledException {
    ui.checkCancelled();
    var result = query.apply(files);
    if (result.isEmpty()) {
      unused.addAll(files);
    }
    else if (files.size() > 1) {
      int half = files.size() / 2;
      collectUnusedFiles(files.subList(0, half), unused, ui, query);
      collectUnusedFiles(files.subList(half, files.size()), unused, ui, query);
    }
  }

  @SuppressWarnings({"SpellCheckingInspection", "unused", "UnusedReturnValue"})
  private interface Win32RestartManager extends StdCallLibrary {
    Win32RestartManager INSTANCE = Native.load("RstrtMgr", Win32RestartManager.class);

    int CCH_RM_SESSION_KEY = 32;
    int CCH_RM_MAX_APP_NAME = 255;
    int CCH_RM_MAX_SVC_NAME = 63;

    @Structure.FieldOrder({"dwProcessId", "ProcessStartTime"})
    class RmUniqueProcess extends Structure {
      public int dwProcessId;
      public WinBase.FILETIME ProcessStartTime;
    }

    @Structure.FieldOrder({"Process", "strAppName", "strServiceShortName", "ApplicationType", "AppStatus", "TSSessionId", "bRestartable"})
    class RmProcessInfo extends Structure {
      public Win32RestartManager.RmUniqueProcess Process;
      public char[] strAppName = new char[CCH_RM_MAX_APP_NAME + 1];
      public char[] strServiceShortName = new char[CCH_RM_MAX_SVC_NAME + 1];
      public int ApplicationType;
      public WinDef.LONG AppStatus;
      public int TSSessionId;
      public boolean bRestartable;
    }

    int RmStartSession(IntByReference pSessionHandle, int dwSessionFlags, char[] strSessionKey);

    int RmRegisterResources(int dwSessionHandle,
                            int nFiles,
                            StringArray rgsFilenames,
                            int nApplications,
                            Pointer rgApplications,
                            int nServices,
                            StringArray rgsServiceNames);

    int RmGetList(int dwSessionHandle,
                  IntByReference pnProcInfoNeeded,
                  IntByReference pnProcInfo,
                  Win32RestartManager.RmProcessInfo rgAffectedApps,
                  LongByReference lpdwRebootReasons);

    int RmEndSession(int dwSessionHandle);
  }
}
