package com.jetbrains.python.debugger;

import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.ui.DebuggerIcons;
import org.jetbrains.annotations.NotNull;


// todo: extensive types support
// todo: trim long values
// todo: load long lists by parts
public class PyDebugValue extends XValue {

  private final String myName;
  private final String myType;
  private final String myValue;

  public PyDebugValue(final String name, final String type, final String value) {
    myName = name;
    myType = type;
    myValue = value;
  }

  @Override
  public void computePresentation(@NotNull XValueNode node) {
    node.setPresentation(myName, DebuggerIcons.VALUE_ICON, myType, getValuePresentation(), false);
  }

  @Override
  public void computeChildren(@NotNull XCompositeNode node) {
    // todo: support from pydevd needed (?)
    super.computeChildren(node);
  }

  public String getName() {
    return myName;
  }

  public String getType() {
    return myType;
  }

  public String getValue() {
    return myValue;
  }

  private String getValuePresentation() {
    String presentation;
    if ("str".equals(myType) || "unicode".equals(myType)) {
      presentation = "\"" + myValue + "\"";
    }
    else {
      presentation = myValue;
    }

    return presentation;
  }

}
