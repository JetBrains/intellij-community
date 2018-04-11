package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.PyDebuggerException;
import org.jetbrains.annotations.NotNull;

public class VersionCommand extends AbstractCommand {

  private final String myVersion;
  private final String myPycharmOS;
  private String myRemoteVersion = null;

  public VersionCommand(final RemoteDebugger debugger, final String version, String pycharmOS) {
    super(debugger, VERSION);
    myVersion = version;
    myPycharmOS = pycharmOS;
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
  protected void processResponse(@NotNull final ProtocolFrame response) throws PyDebuggerException {
    super.processResponse(response);
    myRemoteVersion = response.getPayload();
  }

  public String getRemoteVersion() {
    return myRemoteVersion;
  }

}
