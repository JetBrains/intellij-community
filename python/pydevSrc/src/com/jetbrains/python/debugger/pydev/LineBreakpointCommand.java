// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;

import org.jetbrains.annotations.NotNull;

public abstract class LineBreakpointCommand extends AbstractCommand {
  private final String myType;
  @NotNull protected final String myFile;
  protected final int myLine;


  public LineBreakpointCommand(@NotNull RemoteDebugger debugger,
                               String type, int commandCode,
                               @NotNull final String file,
                               final int line) {
    super(debugger, commandCode);
    myType = type;
    myFile = file;
    myLine = line;
  }

  @Override
  protected void buildPayload(Payload payload) {
    payload.add(myType).add(myFile).add(Integer.toString(myLine));
  }
}
