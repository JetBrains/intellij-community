package com.jetbrains.python.debugger.pydev;

import com.intellij.openapi.application.ApplicationManager;
import com.jetbrains.python.debugger.PyDebuggerException;
import org.jetbrains.annotations.NotNull;


public abstract class AbstractCommand<T> {

  public static final int RUN = 101;
  public static final int CREATE_THREAD = 103;
  public static final int KILL_THREAD = 104;
  public static final int SUSPEND_THREAD = 105;
  public static final int RESUME_THREAD = 106;
  public static final int STEP_INTO = 107;
  public static final int STEP_OVER = 108;
  public static final int STEP_OUT = 109;
  public static final int GET_VARIABLE = 110;
  public static final int SET_BREAKPOINT = 111;
  public static final int REMOVE_BREAKPOINT = 112;
  public static final int EVALUATE = 113;
  public static final int GET_FRAME = 114;
  public static final int EXECUTE = 115;
  public static final int WRITE_TO_CONSOLE = 116;
  public static final int CHANGE_VARIABLE = 117;
  public static final int GET_COMPLETIONS = 120;
  public static final int CONSOLE_EXEC = 121;
  public static final int ADD_EXCEPTION_BREAKPOINT = 122;
  public static final int REMOVE_EXCEPTION_BREAKPOINT = 123;
  public static final int LOAD_SOURCE = 124;
  public static final int ADD_DJANGO_EXCEPTION_BREAKPOINT = 125;
  public static final int REMOVE_DJANGO_EXCEPTION_BREAKPOINT = 126;
  public static final int SMART_STEP_INTO = 128;
  public static final int EXIT = 129;
  public static final int CALL_SIGNATURE_TRACE = 130;

  public static final int VERSION = 501;
  public static final String NEW_LINE_CHAR = "@_@NEW_LINE_CHAR@_@";
  public static final String TAB_CHAR = "@_@TAB_CHAR@_@";


  @NotNull protected final RemoteDebugger myDebugger;
  private final int myCommandCode;

  private final ResponseProcessor<T> myResponseProcessor;


  protected AbstractCommand(@NotNull final RemoteDebugger debugger, final int commandCode) {
    myDebugger = debugger;
    myCommandCode = commandCode;
    myResponseProcessor = createResponseProcessor();
  }

  protected ResponseProcessor<T> createResponseProcessor() {
    return null;
  }

  protected ResponseProcessor<T> getResponseProcessor() {
    return myResponseProcessor;
  }

  @NotNull
  public final String getPayload() {
    Payload payload = new Payload();
    buildPayload(payload);
    return payload.getText();
  }

  protected abstract void buildPayload(Payload payload);

  public boolean isResponseExpected() {
    return false;
  }

  public void execute() throws PyDebuggerException {
    final int sequence = myDebugger.getNextSequence();

    final ResponseProcessor<T> processor = getResponseProcessor();

    if (processor != null || isResponseExpected()) {
      myDebugger.placeResponse(sequence, null);
    }

    ProtocolFrame frame = new ProtocolFrame(myCommandCode, sequence, getPayload());
    boolean frameSent = myDebugger.sendFrame(frame);

    if (processor == null && !isResponseExpected()) return;

    if (!frameSent) {
      throw new PyDebuggerException("Couldn't send frame " + myCommandCode);
    }

    frame = myDebugger.waitForResponse(sequence);
    if (frame == null) {
      if (!myDebugger.isConnected()) {
        throw new PyDebuggerException("No connection (command:  " + myCommandCode + " )");
      }
      throw new PyDebuggerException("Timeout waiting for response on " + myCommandCode);
    }
    if (processor != null) {
      processor.processResponse(frame);
    }
    else {
      processResponse(frame);
    }
  }

  public void execute(final ProcessDebugger.DebugCallback<T> callback) {
    final int sequence = myDebugger.getNextSequence();

    final ResponseProcessor<T> processor = getResponseProcessor();

    if (processor != null) {
      myDebugger.placeResponse(sequence, null);
    }

    try {
      ProtocolFrame frame = new ProtocolFrame(myCommandCode, sequence, getPayload());
      boolean frameSent = myDebugger.sendFrame(frame);

      if (processor == null) return;

      if (!frameSent) {
        throw new PyDebuggerException("Couldn't send frame " + myCommandCode);
      }
    }
    catch (PyDebuggerException e) {
      callback.error(e);
      return;
    }

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          ProtocolFrame frame = myDebugger.waitForResponse(sequence);
          if (frame == null) {
            if (!myDebugger.isConnected()) {
              throw new PyDebuggerException("No connection (command:  " + myCommandCode + " )");
            }
            throw new PyDebuggerException("Timeout waiting for response on " + myCommandCode);
          }
          callback.ok(processor.processResponse(frame));
        }
        catch (PyDebuggerException e) {
          callback.error(e);
        }
      }
    });
  }


  protected void processResponse(final ProtocolFrame response) throws PyDebuggerException {
    if (response.getCommand() >= 900 && response.getCommand() < 1000) {
      throw new PyDebuggerException(response.getPayload());
    }
  }

  protected abstract static class ResponseProcessor<T> {
    protected T processResponse(final ProtocolFrame response) throws PyDebuggerException {
      if (response.getCommand() >= 900 && response.getCommand() < 1000) {
        throw new PyDebuggerException(response.getPayload());
      }

      return parseResponse(response);
    }

    protected abstract T parseResponse(ProtocolFrame response) throws PyDebuggerException;
  }

  public static boolean isCallSignatureTrace(int command) {
    return command == CALL_SIGNATURE_TRACE;
  }

  public static boolean isWriteToConsole(final int command) {
    return command == WRITE_TO_CONSOLE;
  }

  public static boolean isExitEvent(final int command) {
    return command == EXIT;
  }

  protected static class Payload {
    private final StringBuilder myBuilder = new StringBuilder();
    private static final char SEPARATOR = '\t';


    public Payload add(boolean flag) {
      return doAdd(flag ? "1" : "0");
    }

    public Payload add(int value) {
      return doAdd(String.valueOf(value));
    }

    public Payload add(String text) {
      return doAdd(text);
    }

    private Payload doAdd(String text) {
      if (myBuilder.length() > 0) {
        return separator().append(text);
      }
      else {
        return append(text);
      }
    }

    private Payload append(String text) {
      myBuilder.append(ProtocolParser.encodeExpression(text));
      return this;
    }

    private Payload separator() {
      myBuilder.append(SEPARATOR);
      return this;
    }

    public String getText() {
      return myBuilder.toString();
    }
  }
}
