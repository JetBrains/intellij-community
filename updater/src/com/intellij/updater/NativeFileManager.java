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

import org.jetbrains.annotations.VisibleForTesting;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;

/**
 * <p>A utility class to find processes that hold a lock to a file. This relies on a Windows API called
 * <a href="http://msdn.microsoft.com/en-us/library/windows/desktop/cc948910(v=vs.85).aspx">RestartManager</a>}.</p>
 *
 * <p>On Windows with Java 22 or newer, the class uses {@link WindowsNative}. Otherwise, it returns an empty list.</p>
 */
public final class NativeFileManager {
  private static final int MAX_PROCESSES = 10;

  private static final Supplier<WindowsNative> NATIVE = WindowsNative.supplier();

  public static final class Process {
    public final int pid;
    public final String name;

    public Process(int pid, String name) {
      this.pid = pid;
      this.name = name;
    }

    public boolean terminate() {
      var nativeApi = NATIVE.get();
      if (nativeApi == null) {
        return false;
      }
      return nativeApi.terminateProcess(pid, name);
    }
  }

  @VisibleForTesting
  public static List<Process> getProcessesUsing(File file, int initialBufferSize) {
    var nativeApi = NATIVE.get();
    if (nativeApi == null) {
      return List.of();
    }
    return nativeApi.processesUsing(file.toPath(), initialBufferSize);
  }

  public static List<Process> getProcessesUsing(File file) {
    return getProcessesUsing(file, MAX_PROCESSES);
  }
}
