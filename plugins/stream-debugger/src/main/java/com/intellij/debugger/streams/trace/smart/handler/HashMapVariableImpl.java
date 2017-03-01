package com.intellij.debugger.streams.trace.smart.handler;

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
    final StringBuilder builder = new StringBuilder();
    final String newLine = System.lineSeparator();
    builder.append("final ").append(arrayName).append(String.format("new Object[%s.size()];", getName())).append(System.lineSeparator());
    builder.append("{").append(newLine);
    builder.append("int i = 0;").append(newLine);
    builder.append("for (final ").append(myFromType).append(String.format(" key : %s.keySet()) {", getName())).append(newLine);
    builder.append("final Object value = ").append(String.format("%s.get(key);", getName())).append(newLine);
    builder.append(String.format("%s[i] = ", arrayName)).append("new Object[] { key, value };").append(newLine);
    builder.append("}").append(newLine);

    return builder.toString();
  }
}
