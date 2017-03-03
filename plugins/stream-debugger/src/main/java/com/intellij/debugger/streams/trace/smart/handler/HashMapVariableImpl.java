package com.intellij.debugger.streams.trace.smart.handler;

import com.intellij.debugger.streams.trace.EvaluateExpressionTracerBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class HashMapVariableImpl extends VariableImpl {
  private final String myFromType;

  public HashMapVariableImpl(@NotNull String name, @NotNull String from, @NotNull String to, boolean isLinked) {
    super(String.format("Map<%s, %s>", from, to), name, isLinked ? "new LinkedHashMap<>()" : "new HashMap<>()");
    myFromType = from;
  }

  public String convertToArray(@NotNull String arrayName) {
    final String newLine = EvaluateExpressionTracerBase.LINE_SEPARATOR;

    return "final Object[] " + arrayName + String.format(" = new Object[%s.size()];", getName()) +
           System.lineSeparator() +
           "{" + newLine +
           "int i = 0;" + newLine +
           "for (final " + myFromType + String.format(" key : %s.keySet()) {", getName()) + newLine +
           "final Object value = " + String.format("%s.get(key);", getName()) + newLine +
           String.format("%s[i++] = ", arrayName) + "new Object[] { key, value };" + newLine +
           "  }" + newLine +
           "}" + newLine;
  }
}
