package com.jetbrains.python.remote;

import com.intellij.openapi.util.Pair;
import com.jetbrains.python.debugger.remote.PyPathMappingSettings;

/**
 * @author traff
 */
public interface PyRemoteProcessHandlerBase {
  PyPathMappingSettings getMappingSettings();

  Pair<String, Integer> obtainRemoteSocket() throws PyRemoteInterpreterException;

  void addRemoteForwarding(int remotePort, int localPort);
}
