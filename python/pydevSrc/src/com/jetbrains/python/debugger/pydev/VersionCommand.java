// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Licensed under the terms of the Eclipse Public License (EPL).
package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.PyDebuggerException;
import org.jetbrains.annotations.NotNull;

public class VersionCommand extends AbstractCommand {

  private final String myVersion;
  private final String myPycharmOS;
  private final long myResponseTimeout;
  private String myRemoteVersion = null;

  public VersionCommand(final RemoteDebugger debugger, final String version, String pycharmOS, long responseTimeout) {
    super(debugger, VERSION);
    myVersion = version;
    myPycharmOS = pycharmOS;
    myResponseTimeout = responseTimeout;
  }

  @Override
  protected void buildPayload(Payload payload) {
    payload.add(myVersion).add(myPycharmOS);
  }

  @Override
  public boolean isResponseExpected() {
    return true;
  }

  @Override
  protected long getResponseTimeout() {
    return myResponseTimeout;
  }

  @Override
  protected void processResponse(final @NotNull ProtocolFrame response) throws PyDebuggerException {
    super.processResponse(response);
    myRemoteVersion = response.getPayload();
  }

  public String getRemoteVersion() {
    return myRemoteVersion;
  }

}
