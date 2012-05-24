package com.jetbrains.python.remote;

import com.intellij.openapi.util.Pair;
import com.jetbrains.python.debugger.remote.PyPathMappingSettings;

import java.util.List;

/**
 * @author traff
 */
public interface PyRemoteProcessHandlerBase {
  PyPathMappingSettings getMappingSettings();

  Pair<String, Integer> obtainRemoteSocket() throws PyRemoteInterpreterException;

  void addRemoteForwarding(int remotePort, int localPort);

  List<PyPathMappingSettings.PyPathMapping> getFileMappings();
}
