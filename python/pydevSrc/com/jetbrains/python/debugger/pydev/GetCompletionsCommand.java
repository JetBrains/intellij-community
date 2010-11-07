package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PydevXmlUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author traff
 */
public class GetCompletionsCommand extends AbstractFrameCommand {

  private String myActionToken;
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
  protected void processResponse(ProtocolFrame response) throws PyDebuggerException {
    super.processResponse(response);
    try {
      myCompletions = PydevXmlUtils.xmlToCompletions(response.getPayload());
    }
    catch (Exception e) {
      throw new PyDebuggerException("cant obtain completions", e);
    }
  }

  @Override
  public String getPayload() {
    return new StringBuilder().append(myThreadId).append('\t').append(myFrameId).append('\t').append("FRAME\t")
      .append(myActionToken).toString();
  }

  @Nullable
  public List<PydevCompletionVariant> getCompletions() {
    return myCompletions;
  }
}
