package com.jetbrains.python.debugger;

import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface PyPositionConverter {

  /**
   * @param file filepath in position on Python side
   * @param line line number on Python side
   * @return position in Python file which is used when showing Frames window and suspended position. It must be compatible with
   * Python side, but also navigatable in the IDE editor.
   */
  @NotNull
  PySourcePosition convertPythonToFrame(@NotNull final String file, final int line);

  /**
   * @param position shown in Frames window
   * @return position on Python side
   */
  @NotNull
  PySourcePosition convertFrameToPython(@NotNull PySourcePosition position);

  /**
   * @param position source position in the IDE editor
   * @return position on Python side
   */
  @NotNull
  PySourcePosition convertToPython(@NotNull final XSourcePosition position);

  /**
   * @param position on Python side
   * @return position in the IDE editor
   */
  @Nullable
  default XSourcePosition convertFromPython(@NotNull final PySourcePosition position) {
    return null;
  }

  @Nullable
  default XSourcePosition convertFromPython(@NotNull final PySourcePosition position, String frameName) {
    return convertFromPython(position);
  }

  PySignature convertSignature(PySignature signature);
}
