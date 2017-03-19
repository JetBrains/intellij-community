package com.intellij.debugger.streams.resolve;

import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.trace.TraceInfo;
import com.sun.jdi.Value;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public class IdentityResolver implements ValuesOrderResolver {
  @NotNull
  @Override
  public Result resolve(@NotNull TraceInfo info) {
    final Map<Integer, TraceElement> before = info.getValuesOrderBefore();
    final Map<Integer, TraceElement> after = info.getValuesOrderAfter();

    final Map<TraceElement, List<TraceElement>> direct = new HashMap<>();
    final Map<TraceElement, List<TraceElement>> reverse = new HashMap<>();

    final Map<Value, List<TraceElement>> grouped = new HashMap<>(
      StreamEx.of(after.keySet())
        .sorted()
        .map(after::get)
        .groupingBy(TraceElement::getValue)
    );

    for (final TraceElement element : before.values()) {
      final Value value = element.getValue();

      final List<TraceElement> elements = grouped.get(value);
      final TraceElement afterItem = elements.get(0);

      direct.put(element, Collections.singletonList(afterItem));
      reverse.put(afterItem, Collections.singletonList(element));

      grouped.put(value, elements.isEmpty() ? Collections.emptyList() : elements.subList(1, elements.size()));
    }

    return Result.of(direct, reverse);
  }
}
