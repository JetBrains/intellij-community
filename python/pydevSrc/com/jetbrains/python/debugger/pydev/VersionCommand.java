package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.PyDebuggerException;

public class VersionCommand extends AbstractCommand {

  private final String myVersion;
  private String myRemoteVersion = null;

  public VersionCommand(final RemoteDebugger debugger, final String version) {
    super(debugger, VERSION);
    myVersion = version;
  }

  @Override
  protected void buildPayload(Payload payload) {
    payload.add(myVersion);
  }

  @Override
  public boolean isResponseExpected() {
    return true;
  }

  @Override
  protected void processResponse(final ProtocolFrame response) throws PyDebuggerException {
    super.processResponse(response);
    myRemoteVersion = response.getPayload();
  }

  public String getRemoteVersion() {
    return myRemoteVersion;
  }

}
