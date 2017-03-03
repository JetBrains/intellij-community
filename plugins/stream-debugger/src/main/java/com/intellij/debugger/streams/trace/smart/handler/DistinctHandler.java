package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.trace.EvaluateExpressionTracerBase;
import com.intellij.debugger.streams.wrapper.MethodCall;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class DistinctHandler extends HandlerBase {
  private final PeekTracerHandler myPeekTracer;
  private final HashMapVariableImpl myStoreMapVariable;
  private final HashMapVariableImpl myResolveMapVariable;

  public DistinctHandler(int callNumber, @NotNull String callName) {
    myPeekTracer = new PeekTracerHandler(callNumber, callName);

    final String variablePrefix = callName + callNumber;
    myStoreMapVariable = new HashMapVariableImpl(variablePrefix + "Store", "Object", "Map<Integer, Object>", false);
    myResolveMapVariable = new HashMapVariableImpl(variablePrefix + "Resolve", "Object", "Object", false);
  }

  @NotNull
  @Override
  public List<MethodCall> additionalCallsBefore() {
    final List<MethodCall> result = new ArrayList<>(myPeekTracer.additionalCallsBefore());

    final PeekCall storeCall = new PeekCall(createStoreLambda());
    result.add(storeCall);
    return result;
  }

  @NotNull
  @Override
  public List<MethodCall> additionalCallsAfter() {
    final List<MethodCall> result = new ArrayList<>(myPeekTracer.additionalCallsAfter());
    final MethodCall checkCall = new PeekCall(createResolveLambda());
    result.add(checkCall);
    return result;
  }

  @NotNull
  @Override
  public String prepareResult() {
    final String peekPrepare = myPeekTracer.prepareResult();
    final String resolve2Array = myResolveMapVariable.convertToArray("resolveArray");
    final String peekResult = "final Object peekResult = " + myPeekTracer.getResultExpression() + EvaluateExpressionTracerBase.LINE_SEPARATOR;
    return peekPrepare + resolve2Array + peekResult;
  }

  @NotNull
  @Override
  public String getResultExpression() {
    return "new Object[] { peekResult, resolveArray }";
  }

  @NotNull
  private String createStoreLambda() {
    final String storeMap = myStoreMapVariable.getName();
    return "x -> " +
           String.format("%s.computeIfAbsent(x, () -> new LinkedHashMap<Object>).put(time.get(), x)", storeMap);
  }

  @NotNull
  private String createResolveLambda() {
    final String newLine = EvaluateExpressionTracerBase.LINE_SEPARATOR;
    final String storeMap = myStoreMapVariable.getName();
    final String resolveMap = myResolveMapVariable.getName();

    return "x -> {" + newLine +
           "final Map<Integer, Object> objects = " + String.format("%s.get(x);", storeMap) + newLine +
           "for (final int key: objects) {" + newLine +
           "final Object value = objects.get(key);" + newLine +
           "if (value == x) {" + newLine +
           String.format("%s.put(key, time.get())", resolveMap) + newLine +
           "    }" + newLine +
           "  }" + newLine +
           "}" + newLine;
  }

  @NotNull
  @Override
  protected List<Variable> getVariables() {
    final List<Variable> variables = new ArrayList<>(Arrays.asList(myStoreMapVariable, myResolveMapVariable));
    variables.addAll(myPeekTracer.getVariables());
    return variables;
  }
}
