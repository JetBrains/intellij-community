package com.jetbrains.python.debugger.pydev;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.jetbrains.python.debugger.PyDebuggerException;

public class SetNextStatementCommand extends AbstractThreadCommand {
  private int myLine;
  private int myOffset;
  private String myFunctionName;
  private final Editor myEditor;
  private boolean isSucceeded = false;

  protected SetNextStatementCommand(RemoteDebugger debugger,
                                    String threadId,
                                    XSourcePosition sourcePosition,
                                    String functionName,
                                    Editor editor) {
    super(debugger, SET_NEXT_STATEMENT, threadId);
    myLine = sourcePosition.getLine();
    myOffset = sourcePosition.getOffset();
    myFunctionName = functionName;
    myEditor = editor;
  }

  @Override
  public boolean isResponseExpected() {
    return true;
  }

  @Override
  protected void processResponse(ProtocolFrame response) throws PyDebuggerException {
    super.processResponse(response);
    Pair<Boolean, String> result = ProtocolParser.parseSetNextStatementCommand(response.getPayload());
    if (!result.first) {
      UIUtil.invokeLaterIfNeeded(() -> {
        myEditor.getCaretModel().moveToOffset(myOffset);
        HintManager.getInstance().showErrorHint(myEditor, result.second);
      });
    }
    else {
      isSucceeded = true;
    }
  }

  public boolean isSucceeded() {
    return isSucceeded;
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    String functionName = myFunctionName != null ? myFunctionName : "None";
    payload.add(myLine + 1).add(functionName);
  }
}
