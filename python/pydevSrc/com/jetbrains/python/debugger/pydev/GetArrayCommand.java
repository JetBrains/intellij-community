package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;

/**
 * @author amarch
 */
public class GetArrayCommand extends GetFrameCommand {

  private final String myVariableName;
  private final int myRowOffset;
  private final int myColOffset;
  private final int myRows;
  private final int myColumns;
  private final String myFormat;
  private final String myTempName;
  private Object[][] myArrayItems;

  public GetArrayCommand(final RemoteDebugger debugger, final String threadId, final String frameId, PyDebugValue var, int rowOffset, int colOffset, int rows, int cols, String format) {
    super(debugger, GET_ARRAY, threadId, frameId);
    myVariableName = var.getName();
    myTempName = var.getTempName();
    myRowOffset = rowOffset;
    myColOffset = colOffset;
    myRows = rows;
    myColumns = cols;
    myFormat = format;
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add(myVariableName);
    payload.add(myTempName);
    payload.add(myRowOffset);
    payload.add(myColOffset);
    payload.add(myRows);
    payload.add(myColumns);
    payload.add(myFormat);
  }

  @Override
  protected void processResponse(final ProtocolFrame response) throws PyDebuggerException {
    if (response.getCommand() >= 900 && response.getCommand() < 1000) {
      throw new PyDebuggerException(response.getPayload());
    }
    myArrayItems = ProtocolParser.parseArrayValues(response.getPayload(), myDebugProcess);
  }

  public Object[][] getArray(){
    return myArrayItems;
  }
}
