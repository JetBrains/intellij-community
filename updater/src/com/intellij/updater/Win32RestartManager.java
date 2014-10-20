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

import com.sun.jna.*;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;

public interface Win32RestartManager extends Library {

  Win32RestartManager INSTANCE = (Win32RestartManager) Native.loadLibrary("Rstrtmgr", Win32RestartManager.class);

  int CCH_RM_SESSION_KEY = 32;
  int CCH_RM_MAX_APP_NAME = 255;
  int CCH_RM_MAX_SVC_NAME = 63;

  class RmUniqueProcess extends Structure {
    public int dwProcessId;
    public WinBase.FILETIME ProcessStartTime;
  }

  class RmProcessInfo extends Structure {
    public RmUniqueProcess Process;
    public char[] strAppName = new char[CCH_RM_MAX_APP_NAME + 1];
    public char[] strServiceShortName = new char[CCH_RM_MAX_SVC_NAME + 1];
    public int ApplicationType;
    public WinDef.LONG AppStatus;
    public int TSSessionId;
    public boolean bRestartable;
  }

  int RmGetList(int dwSessionHandle,
                IntByReference pnProcInfoNeeded,
                IntByReference pnProcInfo,
                RmProcessInfo rgAffectedApps,
                LongByReference lpdwRebootReasons);

  int RmStartSession(IntByReference pSessionHandle,
                     int dwSessionFlags,
                     char[] strSessionKey);

  int RmRegisterResources(int dwSessionHandle,
                          int nFiles,
                          StringArray rgsFilenames,
                          int nApplications,
                          Pointer rgApplications,
                          int nServices,
                          StringArray rgsServiceNames);

  int RmEndSession(int dwSessionHandle);
}