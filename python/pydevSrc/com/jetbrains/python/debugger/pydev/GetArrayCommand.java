package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import org.jetbrains.annotations.NotNull;

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
  private ArrayChunk myChunk;

  public GetArrayCommand(final RemoteDebugger debugger, final String threadId, final String frameId, PyDebugValue var, int rowOffset, int colOffset, int rows, int cols, String format) {
    super(debugger, GET_ARRAY, threadId, frameId);
    myVariableName = GetVariableCommand.composeName(var);
    myRowOffset = rowOffset;
    myColOffset = colOffset;
    myRows = rows;
    myColumns = cols;
    myFormat = format;
  }

  @Override
  protected void buildPayload(Payload payload) {
    payload.add(myRowOffset);
    payload.add(myColOffset);
    payload.add(myRows);
    payload.add(myColumns);
    payload.add(myFormat);

    if (myVariableName.contains(GetVariableCommand.BY_ID)) {
      //id instead of frame_id
      payload.add(getThreadId()).add(myVariableName);
    }
    else {
      super.buildPayload(payload);
      payload.add(myVariableName);
    }
  }

  @Override
  protected void processResponse(@NotNull final ProtocolFrame response) throws PyDebuggerException {
    if (response.getCommand() >= 900 && response.getCommand() < 1000) {
      final String payload = response.getPayload();
      if (payload.contains("ExceedingArrayDimensionsException")) {
        throw new IllegalArgumentException(myVariableName + " has more than two dimensions");
      }
      throw new PyDebuggerException(payload);
    }
    myChunk = ProtocolParser.parseArrayValues(response.getPayload(), myDebugProcess);
  }

  public ArrayChunk getArray(){
    return myChunk;
  }
}
