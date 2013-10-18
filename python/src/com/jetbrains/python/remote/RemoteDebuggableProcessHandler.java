package com.jetbrains.python.remote;

import com.intellij.remotesdk.RemoteProcessHandlerBase;
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyPositionConverter;

/**
 * @author yole
 */
public interface RemoteDebuggableProcessHandler extends RemoteProcessHandlerBase {
  PyPositionConverter createPositionConverter(PyDebugProcess debugProcess);
}
