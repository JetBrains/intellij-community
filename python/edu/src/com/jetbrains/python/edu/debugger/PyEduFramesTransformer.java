package com.jetbrains.python.edu.debugger;

import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.PyFramesTransformer;
import com.jetbrains.python.debugger.PyStackFrameInfo;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PyEduFramesTransformer implements PyFramesTransformer {
  @Nullable
  @Override
  public List<PyStackFrameInfo> transformFrames(@Nullable List<PyStackFrameInfo> frames) {
    if (frames == null) {
      return null;
    }
    String debugger = PythonHelpersLocator.getHelperPath(PyDebugRunner.DEBUGGER_MAIN);
    List<PyStackFrameInfo> newFrames = new ArrayList<PyStackFrameInfo>();
    for (PyStackFrameInfo frame : frames) {
      String file = frame.getPosition().getFile();
      if (!debugger.equals(file)) {
        newFrames.add(frame);
      }
    }
    return newFrames;
  }
}
