// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Licensed under the terms of the Eclipse Public License (EPL).
package com.jetbrains.python.debugger.pydev;

import com.intellij.openapi.application.ApplicationManager;
import com.jetbrains.python.debugger.PyDebuggerException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


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
  public static final int SET_NEXT_STATEMENT = 127;
  public static final int SMART_STEP_INTO = 128;
  public static final int EXIT = 129;
  public static final int GET_DESCRIPTION = 148;


  public static final int CALL_SIGNATURE_TRACE = 130;

  public static final int CMD_SET_PY_EXCEPTION = 131;
  public static final int CMD_GET_FILE_CONTENTS = 132;
  public static final int CMD_SET_PROPERTY_TRACE = 133;
  public static final int CMD_EVALUATE_CONSOLE_EXPRESSION = 134;
  public static final int CMD_RUN_CUSTOM_OPERATION = 135;
  public static final int CMD_GET_BREAKPOINT_EXCEPTION = 136;
  public static final int CMD_STEP_CAUGHT_EXCEPTION = 137;
  public static final int CMD_SEND_CURR_EXCEPTION_TRACE = 138;
  public static final int CMD_SEND_CURR_EXCEPTION_TRACE_PROCEEDED = 139;
  public static final int CMD_IGNORE_THROWN_EXCEPTION_AT = 140;
  public static final int CMD_ENABLE_DONT_TRACE = 141;

  public static final int SHOW_CONSOLE = 142;
  public static final int GET_ARRAY = 143;
  public static final int STEP_INTO_MY_CODE = 144;
  public static final int LOG_CONCURRENCY_EVENT = 145;
  public static final int SHOW_RETURN_VALUES = 146;
  public static final int INPUT_REQUESTED = 147;

  public static final int PROCESS_CREATED = 149;
  public static final int SHOW_WARNING = 150;
  public static final int LOAD_FULL_VALUE = 151;

  public static final int CMD_GET_SMART_STEP_INTO_VARIANTS = 160;

  public static final int CMD_SET_UNIT_TESTS_DEBUGGING_MODE = 170;

  public static final int SET_USER_TYPE_RENDERERS = 190;

  // Powerful DataViewer commands
  public static final int CMD_DATAVIEWER_ACTION = 210;
  public static final int CMD_TABLE_EXEC = 211;

  public static final int INTERRUPT_DEBUG_CONSOLE = 212;

  public static final int IMAGE_COMMAND_START_LOAD = 213;
  public static final int IMAGE_COMMAND_CHUNK_LOAD = 214;

  /**
   * The code of the message that means that IDE received
   * {@link #PROCESS_CREATED} message from the Python debugger script.
   */
  public static final int PROCESS_CREATED_MSG_RECEIVED = 159;

  public static final int ERROR = 901;

  public static final int VERSION = 501;
  public static final String NEW_LINE_CHAR = "@_@NEW_LINE_CHAR@_@";
  public static final String TAB_CHAR = "@_@TAB_CHAR@_@";


  private final @NotNull RemoteDebugger myDebugger;
  private final int myCommandCode;

  private final ResponseProcessor<T> myResponseProcessor;


  protected AbstractCommand(final @NotNull RemoteDebugger debugger, final int commandCode) {
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

  public final @NotNull String getPayload() {
    Payload payload = new Payload();
    buildPayload(payload);
    return payload.getText();
  }

  protected abstract void buildPayload(Payload payload);


  public static @NotNull String buildCondition(String expression) {
    String condition;

    if (expression != null) {
      condition = expression.replaceAll("\n", NEW_LINE_CHAR);
      condition = condition.replaceAll("\t", TAB_CHAR);
    }
    else {
      condition = "None";
    }
    return condition;
  }

  public boolean isResponseExpected() {
    return false;
  }

  public final void execute() throws PyDebuggerException {
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

    frame = myDebugger.waitForResponse(sequence, getResponseTimeout());
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

  public final void execute(final PyDebugCallback<? super T> callback) {
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

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        ProtocolFrame frame = myDebugger.waitForResponse(sequence, getResponseTimeout());
        if (frame == null) {
          if (!myDebugger.isConnected()) {
            throw new PyDebuggerException("No connection (command:  " + myCommandCode + " )");
          }
          throw new PyDebuggerException("Timeout waiting for response on " + myCommandCode);
        }
        T exception_occurred = processor.parseException(frame);
        if (exception_occurred != null && exception_occurred.equals("True")) {
          callback.error(new PyDebuggerException("Exception occurred"));
        } else {
          callback.ok(processor.processResponse(frame));
        }
      }
      catch (PyDebuggerException e) {
        callback.error(e);
      }
    });
  }

  /**
   * Returns the timeout for waiting for the response after sending the
   * command.
   * <p>
   * Please note that the timeout has no meaning when
   * {@link #isResponseExpected()} is {@code false}.
   *
   * @return the response timeout
   */
  protected long getResponseTimeout() {
    return RemoteDebugger.RESPONSE_TIMEOUT;
  }

  public static boolean isErrorCommand(int command) {
    return command >= 900 && command < 1000;
  }

  protected void processResponse(final @NotNull ProtocolFrame response) throws PyDebuggerException {
    if (isErrorCommand(response.getCommand())) {
      throw new PyDebuggerException(response.getPayload());
    }
  }

  protected abstract static class ResponseProcessor<T> {
    protected T processResponse(final ProtocolFrame response) throws PyDebuggerException {
      if (isErrorCommand(response.getCommand())) {
        throw new PyDebuggerException(response.getPayload());
      }

      return parseResponse(response);
    }

    protected abstract T parseResponse(ProtocolFrame response) throws PyDebuggerException;

    protected @Nullable T parseException(ProtocolFrame frame) throws PyDebuggerException {
      return null;
    }
  }

  public static boolean isCallSignatureTrace(int command) {
    return command == CALL_SIGNATURE_TRACE;
  }

  public static boolean isConcurrencyEvent(int command) {
    return command == LOG_CONCURRENCY_EVENT;
  }

  public static boolean isWriteToConsole(final int command) {
    return command == WRITE_TO_CONSOLE;
  }

  public static boolean isInputRequested(final int command) {
    return command == INPUT_REQUESTED;
  }

  public static boolean isShowWarningCommand(final int command) {
    return command == SHOW_WARNING;
  }

  public static boolean isExitEvent(final int command) {
    return command == EXIT;
  }

  public static boolean isErrorEvent(int command) {
    return command == ERROR;
  }

  public @NotNull RemoteDebugger getDebugger() {
    return myDebugger;
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
      if (!myBuilder.isEmpty()) {
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
