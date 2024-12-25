// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface PyPositionConverter {
  @Deprecated
  @NotNull
  PySourcePosition create(final @NotNull String file, final int line);

  /**
   * @param file filepath in position on Python side
   * @param line line number on Python side
   * @return position in Python file which is used when showing Frames window and suspended position. It must be compatible with
   * Python side, but also navigatable in the IDE editor.
   */
  @ApiStatus.Experimental
  default @NotNull PySourcePosition convertPythonToFrame(final @NotNull String file, final int line) {
    return create(file, line);
  }

  /**
   * @param position shown in Frames window
   * @return position on Python side
   */
  @ApiStatus.Experimental
  default @NotNull PySourcePosition convertFrameToPython(@NotNull PySourcePosition position) {
    return position;
  }

  /**
   * @param position source position in the IDE editor
   * @return position on Python side
   */
  @NotNull
  PySourcePosition convertToPython(final @NotNull XSourcePosition position);

  /**
   * @param position on Python side
   * @return position in the IDE editor
   */
  default @Nullable XSourcePosition convertFromPython(final @NotNull PySourcePosition position) {
    return null;
  }

  default @Nullable XSourcePosition convertFromPython(final @NotNull PySourcePosition position, String frameName) {
    return convertFromPython(position);
  }

  PySignature convertSignature(PySignature signature);
}
