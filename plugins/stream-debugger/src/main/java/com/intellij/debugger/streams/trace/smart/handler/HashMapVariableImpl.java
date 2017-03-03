package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.trace.EvaluateExpressionTracerBase;
import com.intellij.debugger.streams.trace.smart.handler.type.GenericType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class HashMapVariableImpl extends VariableImpl {
  private final GenericType myKeyType;
  private final GenericType myValueType;

  public HashMapVariableImpl(@NotNull String name, @NotNull GenericType from, @NotNull GenericType to, boolean isLinked) {
    super(String.format("Map<%s, %s>", from.getGenericTypeName(), to.getGenericTypeName()), name,
          isLinked ? "new LinkedHashMap<>()" : "new HashMap<>()");
    myKeyType = from;
    myValueType = to;
  }

  public String convertToArray(@NotNull String arrayName) {
    return convertToArray(arrayName, false, false);
  }

  public String convertToArray(@NotNull String arrayName, boolean usePrimitiveKeys, boolean usePrimitiveValues) {
    final String newLine = EvaluateExpressionTracerBase.LINE_SEPARATOR;

    final String keysType = usePrimitiveKeys ? myKeyType.getVariableName() : myKeyType.getGenericTypeName();
    final String valuesType = usePrimitiveValues ? myValueType.getVariableName() : myValueType.getGenericTypeName();

    return "final Object[] " + arrayName + ";" + newLine +
           "{" + newLine +
           "int i = 0;" + newLine +
           "final int size = " + getName() + ".size();" + newLine +
           "final " + keysType + "[] keys = new Object[size];" + newLine +
           "final " + valuesType + "[] values = new Object[size];" + newLine +
           "for (final " + keysType + String.format(" key : %s.keySet()) {", getName()) + newLine +
           "final " + valuesType + " value = " + String.format("%s.get(key);", getName()) + newLine +
           "keys[i] = key;" + newLine +
           "values[i] = value;" + newLine +
           "i++;" + newLine +
           "  }" + newLine +
           arrayName + " = new Object[] { keys, values };" + newLine +
           "}" + newLine;
  }
}
