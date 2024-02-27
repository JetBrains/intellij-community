// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;

import java.util.Optional;

public class ConsoleExecCommand extends AbstractFrameCommand<String> {
  private final String myExpression;

  public ConsoleExecCommand(final RemoteDebugger debugger, final String threadId, final String frameId, final String expression) {
    super(debugger, CONSOLE_EXEC, threadId, frameId);
    myExpression = expression;
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add("FRAME").add(myExpression);
  }

  @Override
  public boolean isResponseExpected() {
    return true;
  }

  @Override
  protected ResponseProcessor<String> createResponseProcessor() {
    return new ResponseProcessor<>() {
      @Override
      protected String parseResponse(ProtocolFrame response) throws PyDebuggerException {
        final PyDebugValue value = ProtocolParser.parseValue(response.getPayload(), getDebugger().getDebugProcess());
        return value.getValue();
      }
      @Override
      protected String parseException(ProtocolFrame response) throws PyDebuggerException {
        Optional<PyDebugValue> optional = ProtocolParser.parseValues(response.getPayload(), getDebugger().getDebugProcess())
          .stream()
          .filter(elem -> elem.getName().equals("exception_occurred"))
          .findFirst();
        return optional.map(PyDebugValue::getValue).orElse(null);
      }
    };
  }
}
