package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.trace.EvaluateExpressionTracerBase;
import com.intellij.debugger.streams.trace.smart.handler.type.ClassTypeImpl;
import com.intellij.debugger.streams.trace.smart.handler.type.GenericType;
import com.intellij.debugger.streams.wrapper.StreamCall;
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
  private final HashMapVariableImpl myReverseUtilMapVariable;

  public DistinctHandler(int callNumber) {
    myPeekTracer = new PeekTracerHandler(callNumber, "distinct");

    final String variablePrefix = "distinct" + callNumber;
    myStoreMapVariable =
      new HashMapVariableImpl(variablePrefix + "Store", GenericType.OBJECT, new ClassTypeImpl("Map<Integer, Object>"), false);
    myResolveMapVariable = new HashMapVariableImpl(variablePrefix + "Resolve", GenericType.INT, GenericType.INT, false);
    myReverseUtilMapVariable = new HashMapVariableImpl(variablePrefix + "ReverseUtil", GenericType.INT, GenericType.INT, false);
  }

  @NotNull
  @Override
  public List<StreamCall> additionalCallsBefore() {
    final List<StreamCall> result = new ArrayList<>(myPeekTracer.additionalCallsBefore());

    final PeekCall storeCall = new PeekCall(createStoreLambda());
    result.add(storeCall);
    return result;
  }

  @NotNull
  @Override
  public List<StreamCall> additionalCallsAfter() {
    final List<StreamCall> result = new ArrayList<>(myPeekTracer.additionalCallsAfter());
    result.add(new PeekCall(createResolveLambda()));
    return result;
  }

  @NotNull
  @Override
  public String prepareResult() {
    final String newLine = EvaluateExpressionTracerBase.LINE_SEPARATOR;
    final String peekPrepare = myPeekTracer.prepareResult();

    final String storeMapName = myStoreMapVariable.getName();
    final String afterMapName = myPeekTracer.getAfterMapName();
    final String prepareResolveMap = "{" + newLine +
                                     "  for (final int timeAfter : " + myReverseUtilMapVariable.getName() + ".keySet()) {" + newLine +
                                     "    final Object afterValue = " + afterMapName + ".get(timeAfter);" + newLine +
                                     "    final Map<Integer, Object> valuesBefore = " + storeMapName + ".get(afterValue);" + newLine +
                                     "    for (final int timeBefore : valuesBefore.keySet()) {" + newLine +
                                     "      " + myResolveMapVariable.getName() + ".put(timeBefore, timeAfter);" + newLine +
                                     "    }" + newLine +
                                     "  }" + newLine +
                                     "}" + newLine;

    final String peekResult =
      "final Object peekResult = " + myPeekTracer.getResultExpression() + ";" + EvaluateExpressionTracerBase.LINE_SEPARATOR;
    final String resolve2Array = myResolveMapVariable.convertToArray("resolveDirect", true, true);
    return peekPrepare + prepareResolveMap + resolve2Array + peekResult;
  }

  @NotNull
  @Override
  public String getResultExpression() {
    return "new Object[] { peekResult, resolve }";
  }

  @NotNull
  private String createStoreLambda() {
    final String storeMap = myStoreMapVariable.getName();
    return "x -> " + String.format("%s.computeIfAbsent(x, y -> new LinkedHashMap<>()).put(time.get(), x)", storeMap);
  }

  @NotNull
  private String createResolveLambda() {
    final String newLine = EvaluateExpressionTracerBase.LINE_SEPARATOR;
    final String storeMap = myStoreMapVariable.getName();
    final String resolveReverseMap = myReverseUtilMapVariable.getName();

    return "x -> {" + newLine +
           "  final Map<Integer, Object> objects = " + String.format("%s.get(x);", storeMap) + newLine +
           "  for (final int key: objects.keySet()) {" + newLine +
           "    final Object value = objects.get(key);" + newLine +
           "    if (value == x && !" + resolveReverseMap + ".containsKey(key)) {" + newLine +
           "      " + String.format("%s.put(time.get(), key);", resolveReverseMap) + newLine +
           "    }" + newLine +
           "  }" + newLine +
           "}" + newLine;
  }

  @NotNull
  @Override
  protected List<Variable> getVariables() {
    final List<Variable> variables =
      new ArrayList<>(Arrays.asList(myStoreMapVariable, myResolveMapVariable, myReverseUtilMapVariable));
    variables.addAll(myPeekTracer.getVariables());
    return variables;
  }
}
