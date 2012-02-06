package com.jetbrains.python.remote;

import com.jetbrains.python.debugger.remote.PyRemoteDebugConfiguration;

/**
 * @author traff
 */
public interface PyRemoteProcessHandlerBase {
  PyRemoteDebugConfiguration getRemoteDebugConfiguration();
}
