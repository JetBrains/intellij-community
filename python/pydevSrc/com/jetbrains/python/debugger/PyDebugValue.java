package com.jetbrains.python.debugger;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.frame.*;
import com.jetbrains.python.debugger.pydev.PyDebugCallback;
import com.jetbrains.python.debugger.pydev.PyVariableLocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// todo: load long lists by parts
// todo: null modifier for modify modules, class objects etc.
public class PyDebugValue extends XNamedValue {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.pydev.PyDebugValue");
  private static final String DATA_FRAME = "DataFrame";
  private static final String SERIES = "Series";
  private static final Map<String, String> EVALUATOR_POSTFIXES = ImmutableMap.of("ndarray", "Array", DATA_FRAME, DATA_FRAME, SERIES, SERIES);
  public static final int MAX_VALUE = 256;
  public static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();

  public static final String RETURN_VALUES_PREFIX = "__pydevd_ret_val_dict";
  public static final String DEFAULT_VALUE_ASYNC = "__pydevd_value_async";

  private @Nullable String myTempName = null;
  private final @Nullable String myType;
  private final @Nullable String myTypeQualifier;
  private @Nullable String myValue;
  private final boolean myContainer;
  private final boolean myIsReturnedVal;
  private final boolean myIsIPythonHidden;
  private @Nullable PyDebugValue myParent;
  private @Nullable String myId = null;
  private boolean myLoadValueAsync;
  private @NotNull PyFrameAccessor myFrameAccessor;
  private @Nullable PyVariableLocator myVariableLocator;
  private volatile @Nullable XValueNode myLastNode = null;
  private final boolean myErrorOnEval;

  public PyDebugValue(@NotNull final String name,
                      @Nullable final String type,
                      @Nullable String typeQualifier,
                      @Nullable final String value,
                      final boolean container,
                      boolean isReturnedVal,
                      boolean isIPythonHidden,
                      boolean errorOnEval,
                      @NotNull final PyFrameAccessor frameAccessor) {
    this(name, type, typeQualifier, value, container, isReturnedVal, isIPythonHidden, errorOnEval, null, frameAccessor);
  }

  public PyDebugValue(@NotNull final String name,
                      @Nullable final String type,
                      @Nullable String typeQualifier,
                      @Nullable final String value,
                      final boolean container,
                      boolean isReturnedVal,
                      boolean isIPythonHidden,
                      boolean errorOnEval,
                      @Nullable final PyDebugValue parent,
                      @NotNull final PyFrameAccessor frameAccessor) {
    super(name);
    myType = type;
    myTypeQualifier = Strings.isNullOrEmpty(typeQualifier) ? null : typeQualifier;
    myValue = value;
    myContainer = container;
    myIsReturnedVal = isReturnedVal;
    myIsIPythonHidden = isIPythonHidden;
    myErrorOnEval = errorOnEval;
    myParent = parent;
    myFrameAccessor = frameAccessor;
    myLoadValueAsync = false;
    if (DEFAULT_VALUE_ASYNC.equals(myValue)) {
      myLoadValueAsync = true;
      setValue(" ");
    }
  }

  public PyDebugValue(@NotNull PyDebugValue value, @NotNull String newName) {
    this(newName, value.getType(), value.getTypeQualifier(), value.getValue(), value.isContainer(), value.isReturnedVal(),
         value.isIPythonHidden(), value.isErrorOnEval(), value.getParent(), value.getFrameAccessor());
    setLoadValueAsync(value.isLoadValueAsync());
    setTempName(value.getTempName());
  }

  public PyDebugValue(@NotNull PyDebugValue value) {
    this(value, value.getName());
  }

  @Nullable
  public String getTempName() {
    return myTempName != null ? myTempName : myName;
  }

  public void setTempName(@Nullable String tempName) {
    myTempName = tempName;
  }

  @Nullable
  public String getType() {
    return myType;
  }

  public void setValue(@Nullable String newValue) {
    myValue = newValue;
  }

  @Nullable
  public String getValue() {
    return myValue;
  }

  public boolean isContainer() {
    return myContainer;
  }

  public boolean isReturnedVal() {
    return myIsReturnedVal;
  }

  public boolean isIPythonHidden() {
    return myIsIPythonHidden;
  }

  public boolean isErrorOnEval() {
    return myErrorOnEval;
  }

  @Nullable
  public PyDebugValue getParent() {
    return myParent;
  }

  public void setParent(@Nullable PyDebugValue parent) {
    myParent = parent;
  }

  @Nullable
  public PyDebugValue getTopParent() {
    return myParent == null ? this : myParent.getTopParent();
  }

  public boolean isLoadValueAsync() {
    return myLoadValueAsync;
  }

  public void setLoadValueAsync(boolean loadValueAsync) {
    myLoadValueAsync = loadValueAsync;
  }

  @Nullable
  public XValueNode getLastNode() {
    return myLastNode;
  }

  @NotNull
  @Override
  public String getEvaluationExpression() {
    StringBuilder stringBuilder = new StringBuilder();
    buildExpression(stringBuilder);
    return wrapWithPrefix(stringBuilder.toString());
  }

  void buildExpression(@NotNull StringBuilder result) {
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

  @NotNull
  public String getFullName() {
    return wrapWithPrefix(getName());
  }

  @NotNull
  private static String removeId(@NotNull String name) {
    if (name.indexOf('(') != -1) {
      name = name.substring(0, name.indexOf('(')).trim();
    }

    return name;
  }

  @NotNull
  private static String removeLeadingZeros(@NotNull String name) {
    //bugs.python.org/issue15254: "0" prefix for octal
    while (name.length() > 1 && name.startsWith("0")) {
      name = name.substring(1);
    }
    return name;
  }

  private static boolean isLen(@NotNull String name) {
    return "__len__".equals(name);
  }

  @NotNull
  private String wrapWithPrefix(@NotNull String name) {
    if (isReturnedVal()) {
      // return values are saved in dictionary on Python side, so the variable's name should be transformed
      return RETURN_VALUES_PREFIX + "[\"" + name + "\"]";
    }
    else {
      return name;
    }
  }

  @Override
  public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
    String value = PyTypeHandler.format(this);
    setFullValueEvaluator(node, value);
    if (value.length() >= MAX_VALUE) {
      value = value.substring(0, MAX_VALUE);
    }
    node.setPresentation(getValueIcon(), myType, value, myContainer);
  }

  public void updateNodeValueAfterLoading(@NotNull XValueNode node, @NotNull String value, @NotNull String linkText) {
    node.setPresentation(getValueIcon(), myType, value, myContainer);
    if (value.length() >= MAX_VALUE) {
      node.setFullValueEvaluator(new PyFullValueEvaluator(myFrameAccessor, getEvaluationExpression()));
    }
    else {
      node.setFullValueEvaluator(new XFullValueEvaluator() {
        @Override
        public void startEvaluation(@NotNull XFullValueEvaluationCallback callback) {
          callback.evaluated(value);
        }

        @Override
        public String getLinkText() {
          return linkText;
        }

        @Override
        public boolean isShowValuePopup() {
          return false;
        }
      });
    }
  }

  @NotNull
  public PyDebugCallback<String> createDebugValueCallback() {
    return new PyDebugCallback<String>() {
      @Override
      public void ok(String value) {
        myLoadValueAsync = false;
        myValue = value;
        XValueNode node = myLastNode;
        if (node != null && !node.isObsolete()) {
          updateNodeValueAfterLoading(node, value, "");
        }
      }

      @Override
      public void error(PyDebuggerException exception) {
        LOG.error(exception.getMessage());
      }
    };
  }

  public boolean isNumericContainer() {
    return EVALUATOR_POSTFIXES.get(myType) != null;
  }

  @NotNull
  public static List<PyFrameAccessor.PyAsyncValue<String>> getAsyncValuesFromChildren(@NotNull XValueChildrenList childrenList) {
    List<PyFrameAccessor.PyAsyncValue<String>> variables = new ArrayList<>();
    for (int i = 0; i < childrenList.size(); i++) {
      XValue value = childrenList.getValue(i);
      if (value instanceof PyDebugValue) {
        PyDebugValue debugValue = (PyDebugValue)value;
        if (debugValue.isLoadValueAsync() && !debugValue.isNumericContainer()) {
          variables.add(new PyFrameAccessor.PyAsyncValue<>(debugValue, debugValue.createDebugValueCallback()));
        }
      }
    }
    return variables;
  }

  public static void getAsyncValues(@NotNull PyFrameAccessor frameAccessor, @NotNull XValueChildrenList childrenList) {
    List<PyFrameAccessor.PyAsyncValue<String>> variables = getAsyncValuesFromChildren(childrenList);
    int chunkSize = Math.max(1, variables.size() / AVAILABLE_PROCESSORS);
    int left = 0;
    int right = Math.min(chunkSize, variables.size());
    while (left < variables.size()) {
      frameAccessor.loadAsyncVariablesValues(variables.subList(left, right));
      left = right;
      right = Math.min(right + chunkSize, variables.size());
    }
  }

  private void setFullValueEvaluator(@NotNull XValueNode node, @NotNull String value) {
    String treeName = getEvaluationExpression();
    String postfix = EVALUATOR_POSTFIXES.get(myType);
    if (postfix == null) {
      if (value.length() >= MAX_VALUE) {
        node.setFullValueEvaluator(new PyFullValueEvaluator(myFrameAccessor, treeName));
      }
      if (myLoadValueAsync) {
        node.setFullValueEvaluator(new PyLoadingValueEvaluator("... Loading Value", myFrameAccessor, treeName));
        myLastNode = node;
      }
      return;
    }
    String linkText = "...View as " + postfix;
    node.setFullValueEvaluator(new PyNumericContainerValueEvaluator(linkText, myFrameAccessor, treeName));
  }

  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    if (node.isObsolete()) return;
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        final XValueChildrenList values = myFrameAccessor.loadVariable(this);
        if (!node.isObsolete()) {
          node.addChildren(values, true);
          getAsyncValues(myFrameAccessor, values);
        }
      }
      catch (PyDebuggerException e) {
        if (!node.isObsolete()) {
          node.setErrorMessage("Unable to display children:" + e.getMessage());
        }
        LOG.warn(e);
      }
    });
  }

  @NotNull
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

  @NotNull
  public PyFrameAccessor getFrameAccessor() {
    return myFrameAccessor;
  }

  public void setFrameAccessor(@NotNull PyFrameAccessor frameAccessor) {
    myFrameAccessor = frameAccessor;
  }

  @Nullable
  public PyVariableLocator getVariableLocator() {
    return myVariableLocator;
  }

  public void setVariableLocator(@Nullable PyVariableLocator variableLocator) {
    myVariableLocator = variableLocator;
  }

  @Nullable
  public String getId() {
    return myId;
  }

  public void setId(@Nullable String id) {
    myId = id;
  }

  @Override
  public boolean canNavigateToSource() {
    return true;
  }

  @Override
  public void computeSourcePosition(@NotNull XNavigatable navigatable) {
    if (myParent == null) {
      navigatable.setSourcePosition(myFrameAccessor.getSourcePositionForName(myName, null));
    }
    else {
      navigatable.setSourcePosition(myFrameAccessor.getSourcePositionForName(myName, myParent.getDeclaringType()));
    }
  }

  @Override
  public boolean canNavigateToTypeSource() {
    return true;
  }

  private static final  Pattern IS_TYPE_DECLARATION = Pattern.compile("<(?:class|type)\\s*'(?<TYPE>.*?)'>");

  @Override
  public void computeTypeSourcePosition(@NotNull XNavigatable navigatable) {
    String lookupType = getDeclaringType();
    navigatable.setSourcePosition(myFrameAccessor.getSourcePositionForType(lookupType));
  }

  @Nullable
  protected final String getDeclaringType() {
    String lookupType = getQualifiedType();
    if (!Strings.isNullOrEmpty(myValue)) {
      Matcher matcher = IS_TYPE_DECLARATION.matcher(myValue);
      if (matcher.matches()) {
        lookupType = matcher.group("TYPE");
      }
    }
    return lookupType;
  }

  @Nullable
  public String getQualifiedType() {
    if (Strings.isNullOrEmpty(myType))
      return null;
    return (myTypeQualifier == null) ? myType : (myTypeQualifier + "." + myType);
  }

  @Nullable
  public String getTypeQualifier() {
    return myTypeQualifier;
  }

  public boolean isTemporary() {
    return myTempName != null;
  }
}
