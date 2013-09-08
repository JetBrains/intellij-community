package com.jetbrains.python.debugger;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

// todo: load long lists by parts
// todo: null modifier for modify modules, class objects etc.
public class PyDebugValue extends XNamedValue {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.pydev.PyDebugValue");
  public static final int MAX_VALUE = 512;

  private String myTempName = null;
  private final String myType;
  private final String myValue;
  private final boolean myContainer;
  private final PyDebugValue myParent;

  private final PyFrameAccessor myFrameAccessor;

  private final boolean myErrorOnEval;

  public PyDebugValue(@NotNull final String name, final String type, final String value, final boolean container, boolean errorOnEval) {
    this(name, type, value, container, errorOnEval, null, null);
  }

  public PyDebugValue(@NotNull final String name, final String type, final String value, final boolean container,
                      boolean errorOnEval, final PyFrameAccessor frameAccessor) {
    this(name, type, value, container, errorOnEval, null, frameAccessor);
  }

  public PyDebugValue(@NotNull final String name, final String type, final String value, final boolean container,
                      boolean errorOnEval, final PyDebugValue parent, final PyFrameAccessor frameAccessor) {
    super(name);
    myType = type;
    myValue = value;
    myContainer = container;
    myErrorOnEval = errorOnEval;
    myParent = parent;
    myFrameAccessor = frameAccessor;
  }

  public String getTempName() {
    return myTempName != null ? myTempName : myName;
  }

  public void setTempName(String tempName) {
    myTempName = tempName;
  }

  public String getType() {
    return myType;
  }

  public String getValue() {
    return myValue;
  }

  public boolean isContainer() {
    return myContainer;
  }

  public boolean isErrorOnEval() {
    return myErrorOnEval;
  }
  
  public PyDebugValue setParent(@Nullable PyDebugValue parent) {
    return new PyDebugValue(myName, myType, myValue, myContainer, myErrorOnEval, parent, myFrameAccessor);
  }

  public PyDebugValue getParent() {
    return myParent;
  }

  public PyDebugValue getTopParent() {
    return myParent == null ? this : myParent.getTopParent();
  }

  @Override
  public String getEvaluationExpression() {
    StringBuilder stringBuilder = new StringBuilder();
    buildExpression(stringBuilder);
    return stringBuilder.toString();
  }

  void buildExpression(StringBuilder result) {
    if (myParent == null) {
      result.append(getTempName());
    }
    else {
      myParent.buildExpression(result);
      if (("dict".equals(myParent.getType()) || "list".equals(myParent.getType()) || "tuple".equals(myParent.getType())) &&
          !isLen(myName)) {
        result.append('[').append(removeId(myName)).append(']');
      }
      else if (("set".equals(myParent.getType())) && !isLen(myName)) {
        //set doesn't support indexing
      }
      else if (isLen(myName)) {
        result.append('.').append(myName).append("()");
      }
      else {
        result.append('.').append(myName);
      }
    }
  }

  private static String removeId(@NotNull String name) {
    if (name.indexOf('(') != -1) {
      name = name.substring(0, name.indexOf('(')).trim();
    }

    return name;
  }

  private static boolean isLen(String name) {
    return "__len__".equals(name);
  }

  @Override
  public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
    String value = PyTypeHandler.format(this);

    if (value.length() >= MAX_VALUE) {
      node.setFullValueEvaluator(new PyFullValueEvaluator(myFrameAccessor, myName));
      value = value.substring(0, MAX_VALUE);
    }

    node.setPresentation(getValueIcon(), myType, value, myContainer);
  }

  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    if (node.isObsolete()) return;
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        if (myFrameAccessor == null) return;

        try {
          final XValueChildrenList values = myFrameAccessor.loadVariable(PyDebugValue.this);
          if (!node.isObsolete()) {
            node.addChildren(values, true);
          }
        }
        catch (PyDebuggerException e) {
          if (!node.isObsolete()) {
            node.setErrorMessage("Unable to display children:" + e.getMessage());
          }
          LOG.warn(e);
        }
      }
    });
  }

  @Override
  public XValueModifier getModifier() {
    return new PyValueModifier(myFrameAccessor, this);
  }

  private Icon getValueIcon() {
    if (!myContainer) {
      return AllIcons.Debugger.Db_primitive;
    }
    else if ("list".equals(myType) || "tuple".equals(myType)) {
      return AllIcons.Debugger.Db_array;
    }
    else {
      return AllIcons.Debugger.Value;
    }
  }
  
  public PyDebugValue setName(String newName) {
    return new PyDebugValue(newName, myType, myValue, myContainer, myErrorOnEval, myParent, myFrameAccessor);
  }
}
