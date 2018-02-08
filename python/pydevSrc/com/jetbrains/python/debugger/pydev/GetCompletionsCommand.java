package com.jetbrains.python.debugger.pydev;

import com.google.common.collect.Lists;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PydevXmlUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author traff
 */
public class GetCompletionsCommand extends AbstractFrameCommand {

  private final String myActionToken;
  private List<PydevCompletionVariant> myCompletions = null;

  public GetCompletionsCommand(final RemoteDebugger debugger,
                               String threadId,
                               String frameId,
                               final String myActionToken) {
    super(debugger, GET_COMPLETIONS, threadId, frameId);
    this.myActionToken = myActionToken;
  }


  @Override
  public boolean isResponseExpected() {
    return true;
  }

  @Override
  protected void processResponse(@NotNull ProtocolFrame response) throws PyDebuggerException {
    super.processResponse(response);
    try {
      myCompletions = PydevXmlUtils.xmlToCompletions(response.getPayload(), myActionToken);
    }
    catch (Exception e) {
      throw new PyDebuggerException("cant obtain completions", e);
    }
  }


  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add("FRAME").add(myActionToken);
  }

  @NotNull
  public List<PydevCompletionVariant> getCompletions() {
    if (myCompletions != null) {
      return myCompletions;
    }
    else {
      return Lists.newArrayList();
    }
  }
}
