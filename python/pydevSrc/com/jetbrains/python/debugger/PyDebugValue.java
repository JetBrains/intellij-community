package com.jetbrains.python.debugger;

import com.google.common.base.Strings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.frame.*;
import com.jetbrains.python.debugger.pydev.PyVariableLocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

// todo: load long lists by parts
// todo: null modifier for modify modules, class objects etc.
public class PyDebugValue extends XNamedValue {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.pydev.PyDebugValue");
  public static final int MAX_VALUE = 256;

  private String myTempName = null;
  private final String myType;
  private final String myTypeQualifier;
  private final String myValue;
  private final boolean myContainer;
  private final PyDebugValue myParent;
  private String myId = null;

  private final PyFrameAccessor myFrameAccessor;

  private PyVariableLocator myVariableLocator;

  private final boolean myErrorOnEval;

  public PyDebugValue(@NotNull final String name, final String type, String typeQualifier, final String value, final boolean container,
                      boolean errorOnEval, final PyFrameAccessor frameAccessor) {
    this(name, type, typeQualifier, value, container, errorOnEval, null, frameAccessor);
  }

  public PyDebugValue(@NotNull final String name, final String type, String typeQualifier, final String value, final boolean container,
                      boolean errorOnEval, final PyDebugValue parent, final PyFrameAccessor frameAccessor) {
    super(name);
    myType = type;
    myTypeQualifier = Strings.isNullOrEmpty(typeQualifier) ? null : typeQualifier;
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
    return new PyDebugValue(myName, myType, null, myValue, myContainer, myErrorOnEval, parent, myFrameAccessor);
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
        result.append('[').append(removeLeadingZeros(removeId(myName))).append(']');
      }
      else if (("set".equals(myParent.getType())) && !isLen(myName)) {
        //set doesn't support indexing
      }
      else if (isLen(myName)) {
        result.append('.').append(myName).append("()");
      }
      else if (("ndarray".equals(myParent.getType()) || "matrix".equals(myParent.getType())) && myName.startsWith("[")) {
        result.append(removeLeadingZeros(myName));
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

  private static String removeLeadingZeros(@NotNull String name) {
    //bugs.python.org/issue15254: "0" prefix for octal
    while (name.length() > 1 && name.startsWith("0")) {
      name = name.substring(1);
    }
    return name;
  }

  private static boolean isLen(String name) {
    return "__len__".equals(name);
  }

  private static boolean isCollection(@NotNull PyDebugValue parent) {
    String type = parent.getType();
    return type.equals("dict") || type.equals("list");
  }

  private static String getChildNamePresentation(@NotNull PyDebugValue parent, @NotNull String childName) {
    if (isCollection(parent)) {
      return "[".concat(removeId(childName)).concat("]");
    }
    else {
      return ".".concat(childName);
    }
  }

  private String getFullName() {
    String result = "";
    String curNodeName = myName;
    PyDebugValue parent = myParent;
    while (parent != null) {
      result = getChildNamePresentation(parent, curNodeName).concat(result);
      curNodeName = parent.getName();
      parent = parent.getParent();
    }
    return curNodeName.concat(result);
  }

  @Override
  public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
    String value = PyTypeHandler.format(this);

    if (value.length() >= MAX_VALUE) {
      node.setFullValueEvaluator(new PyFullValueEvaluator(myFrameAccessor, getFullName()));
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
    return new PyDebugValue(newName, myType, null, myValue, myContainer, myErrorOnEval, myParent, myFrameAccessor);
  }

  @Nullable
  @Override
  public XReferrersProvider getReferrersProvider() {
    if (myFrameAccessor.getReferrersLoader() != null) {
      return new XReferrersProvider() {
        @Override
        public XValue getReferringObjectsValue() {
          return new PyReferringObjectsValue(PyDebugValue.this);
        }
      };
    } else {
      return null;
    }
  }

  public PyFrameAccessor getFrameAccessor() {
    return myFrameAccessor;
  }

  public PyVariableLocator getVariableLocator() {
    return myVariableLocator;
  }

  public void setVariableLocator(PyVariableLocator variableLocator) {
    myVariableLocator = variableLocator;
  }

  public String getId() {
    return myId;
  }

  public void setId(String id) {
    myId = id;
  }

  @Override
  public boolean canNavigateToSource() {
    return true;
  }

  @Override
  public void computeSourcePosition(@NotNull XNavigatable navigatable) {
    navigatable.setSourcePosition(myFrameAccessor.getSourcePositionForName(myName));
  }

  @Override
  public boolean canNavigateToTypeSource() {
    return true;
  }

  @Override
  public void computeTypeSourcePosition(@NotNull XNavigatable navigatable) {

    navigatable.setSourcePosition(myFrameAccessor.getSourcePositionForType(getQualifiedType()));
  }

  public String getQualifiedType() {
    if (Strings.isNullOrEmpty(myType))
      return null;
    return (myTypeQualifier == null) ? myType : (myTypeQualifier + "." + myType);
  }

  public String getTypeQualifier() {
    return myTypeQualifier;
  }
}
