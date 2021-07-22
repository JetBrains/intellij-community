// Licensed under the terms of the Eclipse Public License (EPL).
package com.jetbrains.python.debugger.pydev;

import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyFrameAccessor;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public class GetVariableCommand extends GetFrameCommand {

  public static final String BY_ID = "BY_ID";
  private final String myVariableName;
  private final PyDebugValue myParent;
  private XValueChildrenList myVariables = null;
  private static final String LEFT_PAREN_CHAR = "@_@LEFT_PAREN_CHAR@_@";
  private static final String RIGHT_PAREN_CHAR = "@_@RIGHT_PAREN_CHAR@_@";

  public GetVariableCommand(final RemoteDebugger debugger, final String threadId, final String frameId, PyDebugValue var) {
    super(debugger, GET_VARIABLE, threadId, frameId);
    myVariableName = var.getOffset() == 0 ? composeName(var) : var.getOffset() + "\t" + composeName(var);
    myParent = var;
  }

  /**
   * Return a full path in a variables tree from the top-level parent to the debug value
   *
   * @param var a debug variable
   * @return A string of attributes in a path separated by \t
   */
  @NotNull
  public static String composeName(final PyDebugValue var) {
    final StringBuilder sb = new StringBuilder();
    PyDebugValue p = var;

    while (p != null) {
      if (sb.length() > 0) {
        sb.insert(0, '\t');
      }
      if (p.getId() != null) {
        sb.insert(0, BY_ID).insert(0, '\t').insert(0, p.getId());
        break;
      }
      else {
        final String tempName = p.getTempName();
        if (tempName != null) {
          sb.insert(0, tempName.replaceAll("\t", TAB_CHAR));
        }
      }
      p = p.getParent();
    }

    return sb.toString();
  }

  @Override
  protected void buildPayload(Payload payload) {
    if (myVariableName.contains(BY_ID)) {
      //id instead of frame_id
      payload.add(getThreadId()).add(myVariableName);
    }
    else {
      super.buildPayload(payload);
      payload.add(myVariableName);
    }
  }

  @Override
  protected void processValues(List<PyDebugValue> values) {
    myVariables = new XValueChildrenList(values.size());
    for (PyDebugValue value : values) {
      if (isTypeRenderersTempVarName(value.getName())) {
        final PyDebugValue debugValue = createTempTypeRenderersValue(value, myDebugProcess);
        myVariables.add(debugValue.getName(), debugValue);
      }
      else if (!value.getName().startsWith(RemoteDebugger.TEMP_VAR_PREFIX)) {
        final PyDebugValue debugValue = extend(value);
        myVariables.add(debugValue.getVisibleName(), debugValue);
      }
    }
  }

  public static boolean isTypeRenderersTempVarName(String name) {
    return name.startsWith(RemoteDebugger.TYPE_RENDERERS_TEMP_VAR_PREFIX);
  }

  public static PyDebugValue createTempTypeRenderersValue(final PyDebugValue value, PyFrameAccessor frameAccessor) {
    String newName = value.getName()
      .replace(RemoteDebugger.TYPE_RENDERERS_TEMP_VAR_PREFIX, "")
      .replace(LEFT_PAREN_CHAR, "(")
      .replace(RIGHT_PAREN_CHAR, ")");
    PyDebugValue debugValue = new PyDebugValue(value, newName);
    debugValue.setTempName(value.getName());
    debugValue.setParent(null);
    debugValue.setFrameAccessor(frameAccessor);
    return debugValue;
  }

  @Override
  protected PyDebugValue extend(final PyDebugValue value) {
    PyDebugValue debugValue = new PyDebugValue(value);
    debugValue.setParent(myParent);
    debugValue.setFrameAccessor(myDebugProcess);
    return debugValue;
  }

  @Override
  public XValueChildrenList getVariables() {
    return myVariables;
  }
}
