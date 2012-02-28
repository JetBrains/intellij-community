package com.jetbrains.python.remote;

import com.jetbrains.python.debugger.remote.PyPathMappingSettings;

/**
 * @author traff
 */
public interface PyRemoteProcessHandlerBase {
  PyPathMappingSettings getMappingSettings();
}
