// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation;
import com.jetbrains.python.PydevBundle;
import com.jetbrains.python.debugger.pydev.PyDebugCallback;
import com.jetbrains.python.debugger.render.PyNodeRenderer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.python.debugger.PyDebugValueGroupsKt.*;

public class PyDebugValue extends XNamedValue {
  private static final Logger LOG = Logger.getInstance(PyDebugValue.class);
  private static final String DATA_FRAME = "DataFrame";
  private static final String SERIES = "Series";
  private static final Map<String, String> EVALUATOR_POSTFIXES = ImmutableMap.of(
    "ndarray", "Array",
    DATA_FRAME, DATA_FRAME,
    SERIES, SERIES,
    "GeoDataFrame", DATA_FRAME,
    "GeoSeries", SERIES
    );
  private static final int MAX_ITEMS_TO_HANDLE = 100;
  public static final int MAX_VALUE = 256;
  public static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();

  public static final String RETURN_VALUES_PREFIX = "__pydevd_ret_val_dict";

  private @Nullable String myTempName = null;
  private final @Nullable String myType;
  private final @Nullable String myTypeQualifier;
  private @Nullable String myValue;
  private final boolean myContainer;
  private final @Nullable String myShape;
  private final boolean myIsReturnedVal;
  private final boolean myIsIPythonHidden;
  private @Nullable PyDebugValue myParent;
  private @Nullable String myId = null;
  private ValuesPolicy myLoadValuePolicy;
  private @NotNull PyFrameAccessor myFrameAccessor;
  private @NotNull final List<XValueNode> myValueNodes = new ArrayList<>();
  private final boolean myErrorOnEval;
  private int myOffset;
  private int myCollectionLength = -1;

  private @NotNull PyDebugValueDescriptor myDescriptor = new PyDebugValueDescriptor();

  public enum ValuesPolicy {
    SYNC, ASYNC, ON_DEMAND
  }

  private static final Map<String, ValuesPolicy> POLICY_DEFAULT_VALUES = ImmutableMap.of("__pydevd_value_async", ValuesPolicy.ASYNC,
                                                                                         "__pydevd_value_on_demand",
                                                                                         ValuesPolicy.ON_DEMAND);

  public static final Map<ValuesPolicy, String> POLICY_ENV_VARS = ImmutableMap.of(ValuesPolicy.ASYNC, "PYDEVD_LOAD_VALUES_ASYNC",
                                                                                  ValuesPolicy.ON_DEMAND, "PYDEVD_LOAD_VALUES_ON_DEMAND");

  public PyDebugValue(@NotNull final String name,
                      @Nullable final String type,
                      @Nullable String typeQualifier,
                      @Nullable final String value,
                      final boolean container,
                      @Nullable String shape,
                      boolean isReturnedVal,
                      boolean isIPythonHidden,
                      boolean errorOnEval,
                      @NotNull final PyFrameAccessor frameAccessor) {
    this(name, type, typeQualifier, value, container, shape, isReturnedVal, isIPythonHidden, errorOnEval, null, frameAccessor);
  }

  /**
   * Represents instance of a Python variable available at runtime. Used in Debugger and various Variable Viewers
   *
   * @param name            variable name
   * @param type            variable type
   * @param typeQualifier   type qualifier
   * @param value           string representation of a value
   * @param container       does variable have fields for expanding
   * @param shape           variable's shape field (available for numeric containers)
   * @param isReturnedVal   is value was returned from a function during debug session
   * @param isIPythonHidden does value belong to IPython util variables group
   * @param errorOnEval     did an error occur during evaluation
   * @param parent          parent variable in Variables tree
   * @param frameAccessor   frame accessor used for evaluation
   */
  public PyDebugValue(@NotNull final String name,
                      @Nullable final String type,
                      @Nullable String typeQualifier,
                      @Nullable final String value,
                      final boolean container,
                      @Nullable String shape,
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
    myShape = shape;
    myIsReturnedVal = isReturnedVal;
    myIsIPythonHidden = isIPythonHidden;
    myErrorOnEval = errorOnEval;
    myParent = parent;
    myFrameAccessor = frameAccessor;
    myLoadValuePolicy = ValuesPolicy.SYNC;
    if (POLICY_DEFAULT_VALUES.containsKey(myValue)) {
      myLoadValuePolicy = POLICY_DEFAULT_VALUES.get(myValue);
      setValue(" ");
    }
  }

  public PyDebugValue(@NotNull PyDebugValue value, @NotNull String newName) {
    this(newName, value.getType(), value.getTypeQualifier(), value.getValue(), value.isContainer(), value.getShape(), value.isReturnedVal(),
         value.isIPythonHidden(), value.isErrorOnEval(), value.getParent(), value.getFrameAccessor());
    myOffset = value.getOffset();
    setLoadValuePolicy(value.getLoadValuePolicy());
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
  public @NlsSafe String getValue() {
    return myValue;
  }

  public boolean isContainer() {
    return myContainer;
  }

  @Nullable
  public String getShape() {
    return myShape;
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

  public ValuesPolicy getLoadValuePolicy() {
    return myLoadValuePolicy;
  }

  public void setLoadValuePolicy(ValuesPolicy loadValueAsync) {
    myLoadValuePolicy = loadValueAsync;
  }

  @NotNull
  public List<XValueNode> getValueNodes() {
    return myValueNodes;
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
      else if (("ndarray".equals(myParent.getType()) || "matrix".equals(myParent.getType())) && myName.equals("array")) {
        // return the string representation of an ndarray
      }
      else if ("array".equals(myParent.getName()) && myParent.myParent != null && "ndarray".equals(myParent.myParent.getType())) {
        result.append("[").append(removeLeadingZeros(myName)).append("]");
      }
      else {
        result.append('.').append(myName);
      }
    }
  }

  /**
   * Evaluate full name to access variable at runtime
   * Used for evaluation values of "Returned values". They are saved in a separate dictionary on Python side, so the variable's name
   * should be transformed.
   *
   * @return full variable name at runtime
   */
  @NlsSafe
  @NotNull
  public String getFullName() {
    return wrapWithPrefix(getName());
  }

  /**
   * Remove util information from variable name to hide it from user
   *
   * @return variable name without util information
   */
  @NotNull
  public String getVisibleName() {
    return removeId(myName);
  }

  /**
   * Removes object id from variable name. Object id is saved inside dict keys to find object at runtime
   *
   * @param name variable name with or without object id ('a' (11259136))
   * @return variable name without object id ('a')
   */
  @NotNull
  private static String removeId(@NotNull String name) {
    if (name.endsWith(")")) {
      final int lastInd = name.lastIndexOf('(');
      if (lastInd != -1) {
        name = name.substring(0, lastInd).trim();
      }
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
    return DUNDER_LEN.equals(name);
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

  private String getTypeString() {
    if (myShape != null) {
      return myType + ": " + myShape;
    }
    return myType;
  }

  private void setElementPresentation(@NotNull XValueNode node, @NotNull String value) {
    if (myParent != null && "set".equals(myParent.getType())) {
      // hide object id and '=' when showing set elements
      node.setPresentation(getValueIcon(), new XRegularValuePresentation(value, getTypeString()) {
        @NotNull
        @Override
        public String getSeparator() {
          return myName.equals(DUNDER_LEN) ? " = " : "";
        }

        @Override
        public boolean isShowName() {
          return myName.equals(DUNDER_LEN);
        }
      }, myContainer);
    }
    else {
      node.setPresentation(getValueIcon(), getTypeString(), value, myContainer);
    }
  }

  @Override
  public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
    String value = PyTypeHandler.format(this);
    setFullValueEvaluator(node, value);
    if (value.length() >= MAX_VALUE) {
      value = value.substring(0, MAX_VALUE);
    }
    value = applyRendererIfApplicable(value);
    setElementPresentation(node, value);
  }

  public void updateNodeValueAfterLoading(@NotNull XValueNode node,
                                          @NotNull String value,
                                          @NotNull @Nls String linkText,
                                          @Nullable String errorMessage) {
    if (errorMessage != null) {
      node.setPresentation(getValueIcon(), new XRegularValuePresentation(value, myType) {
        @Override
        public void renderValue(@NotNull XValueTextRenderer renderer) {
          renderer.renderError(errorMessage);
        }
      }, myContainer);
    }
    else {
      setElementPresentation(node, value);
    }

    if (isNumericContainer()) return; // do not update FullValueEvaluator not to break Array Viewer
    if (value.length() >= MAX_VALUE) {
      node.setFullValueEvaluator(new PyFullValueEvaluator(myFrameAccessor, getEvaluationExpression()));
    }
    else {
      node.setFullValueEvaluator(new XFullValueEvaluator(linkText) {
        @Override
        public void startEvaluation(@NotNull XFullValueEvaluationCallback callback) {
          callback.evaluated(value);
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
    return new PyDebugCallback<>() {
      @Override
      public void ok(String value) {
        myLoadValuePolicy = ValuesPolicy.SYNC;
        myValue = value;
        for (XValueNode node : myValueNodes) {
          if (node != null && !node.isObsolete()) {
            updateNodeValueAfterLoading(node, value, "", null);
          }
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
        if (debugValue.getLoadValuePolicy() == ValuesPolicy.ASYNC) {
          variables.add(new PyFrameAccessor.PyAsyncValue<>(debugValue, debugValue.createDebugValueCallback()));
        }
      }
    }
    return variables;
  }

  public static void getAsyncValues(@Nullable XStackFrame frame, @NotNull PyFrameAccessor frameAccessor, @NotNull XValueChildrenList childrenList) {
    List<PyFrameAccessor.PyAsyncValue<String>> variables = getAsyncValuesFromChildren(childrenList);
    int chunkSize = Math.max(1, variables.size() / AVAILABLE_PROCESSORS);
    int left = 0;
    int right = Math.min(chunkSize, variables.size());
    while (left < variables.size()) {
      frameAccessor.loadAsyncVariablesValues(frame, variables.subList(left, right));
      left = right;
      right = Math.min(right + chunkSize, variables.size());
    }
  }

  private void setFullValueEvaluator(@NotNull XValueNode node, @NotNull String value) {
    String treeName = getEvaluationExpression();
    String postfix = EVALUATOR_POSTFIXES.get(myType);
    myValueNodes.add(node);
    if (postfix == null) {
      if (value.length() >= MAX_VALUE) {
        node.setFullValueEvaluator(new PyFullValueEvaluator(myFrameAccessor, treeName));
      }
      if (myLoadValuePolicy == ValuesPolicy.ASYNC) {
        node.setFullValueEvaluator(new PyLoadingValueEvaluator(PydevBundle.message("pydev.loading.value"), myFrameAccessor, treeName));
      }
      else if (myLoadValuePolicy == ValuesPolicy.ON_DEMAND) {
        node.setFullValueEvaluator(new PyOnDemandValueEvaluator(PydevBundle.message("pydev.show.value"), myFrameAccessor, this, node));
      }
      return;
    }
    String linkText = PydevBundle.message("pydev.view.as", postfix);
    node.setFullValueEvaluator(new PyNumericContainerValueEvaluator(linkText, myFrameAccessor, treeName));
  }

  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    if (node.isObsolete()) return;
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        XValueChildrenList values = myFrameAccessor.loadVariable(this);

        restoreChildrenRenderers(values);

        if (!node.isObsolete()) {
          updateLengthIfIsCollection(values);

          if (isLargeCollection()) {
            values = processLargeCollection(values);
          }
          if (myFrameAccessor.isSimplifiedView()) {
            extractChildrenToGroup(PROTECTED_ATTRS_NAME, AllIcons.Nodes.C_protected, node, values, (String name) -> name.startsWith("_"),
                                   getPROTECTED_ATTRS_EXCLUDED());
          }
          else {
            node.addChildren(values, true);
          }
          if (isLargeCollection()) {
            updateOffset(node, values);
          }

          getAsyncValues(null, myFrameAccessor, values);
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
  public String getId() {
    return myId;
  }

  public void setId(@Nullable String id) {
    myId = id;
  }

  @Override
  public void computeSourcePosition(@NotNull XNavigatable navigatable) {
    ApplicationManager.getApplication().executeOnPooledThread(
      () -> ReadAction.run(
        () -> {
          if (myParent == null) {
            navigatable.setSourcePosition(myFrameAccessor.getSourcePositionForName(myName, null));
          }
          else {
            navigatable.setSourcePosition(myFrameAccessor.getSourcePositionForName(myName, myParent.getDeclaringType()));
          }
        }
      )
    );
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

  public int getOffset() {
    return myOffset;
  }

  public void setOffset(int offset) {
    myOffset = offset;
  }

  private boolean isLargeCollection() {
    return myCollectionLength > MAX_ITEMS_TO_HANDLE;
  }

  private void updateLengthIfIsCollection(final XValueChildrenList values) {
    if (myCollectionLength > 0 && values.size() == 0) return;

    final int lastIndex = values.size() - 1;

    // If there is the __len__ attribute it should always goes last.
    if (values.size() > 0 && isLen(values.getName(lastIndex))) {
      PyDebugValue len = (PyDebugValue)values.getValue(lastIndex);
      try {
        if (myCollectionLength == -1 && len.getValue() != null)
          myCollectionLength = Integer.parseInt(len.getValue());
      } catch (NumberFormatException ex) {
        // Do nothing.
      }
    }
  }

  private XValueChildrenList processLargeCollection(final XValueChildrenList values) {
    if (values.size() > 0 && isLargeCollection()) {
      if (myOffset + Math.min(MAX_ITEMS_TO_HANDLE, values.size()) < myCollectionLength) {
        XValueChildrenList newValues = new XValueChildrenList();
        for(int i = 0; i < values.size() - 1; i++) {
          newValues.add(values.getName(i), values.getValue(i));
        }
        return newValues;
      }
    }
    return values;
  }

  private void updateOffset(final XCompositeNode node, final XValueChildrenList values) {
    if (myContainer && isLargeCollection()) {
      if (myOffset + Math.min(values.size(), MAX_ITEMS_TO_HANDLE) >= myCollectionLength) {
        myOffset = myCollectionLength;
      }
      else {
        node.tooManyChildren(myCollectionLength - Math.min(values.size(), MAX_ITEMS_TO_HANDLE) - myOffset);
        myOffset += Math.min(values.size(), MAX_ITEMS_TO_HANDLE);
      }
    }
  }

  @NotNull
  public PyDebugValueDescriptor getDescriptor() {
    return myDescriptor;
  }

  public void setDescriptor(@NotNull PyDebugValueDescriptor descriptor) {
    myDescriptor = descriptor;
  }

  private void restoreChildrenRenderers(XValueChildrenList values) {
    PyDebugValueDescriptor descriptor = getDescriptor();
    Map<String, PyDebugValueDescriptor> childrenDescriptors = descriptor.getChildrenDescriptors();

    if (childrenDescriptors == null) {
      childrenDescriptors = Maps.newHashMap();
      descriptor.setChildrenDescriptors(childrenDescriptors);
    }

    if (values == null) return;

    for (int i = 0; i < values.size(); i++) {
      if (values.getValue(i) instanceof PyDebugValue) {
        PyDebugValue value = (PyDebugValue) values.getValue(i);
        descriptor = childrenDescriptors.getOrDefault(value.getName(), null);
        if (descriptor == null) {
          descriptor = new PyDebugValueDescriptor();
          childrenDescriptors.put(value.getName(), descriptor);
        }
        value.setDescriptor(descriptor);
      }
    }
  }

  private String applyRendererIfApplicable(String value) {
    final PyNodeRenderer renderer = getDescriptor().getRenderer();
    final String type = getType();
    if (renderer == null || type == null) return value;
    if (renderer.isApplicable(type)) {
      return renderer.render(value);
    }
    else {
      getDescriptor().setRenderer(null);
    }
    return value;
  }
}
