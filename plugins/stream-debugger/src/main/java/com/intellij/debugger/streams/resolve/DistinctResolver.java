package com.intellij.debugger.streams.resolve;

import com.intellij.debugger.streams.trace.TraceElement;
import com.intellij.debugger.streams.trace.TraceInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public class DistinctResolver implements ValuesOrderResolver {
  @NotNull
  @Override
  public Result resolve(@NotNull TraceInfo info) {
    final Map<TraceElement, List<TraceElement>> direct = info.getDirectTrace();
    final Map<TraceElement, List<TraceElement>> reverse = info.getReverseTrace();
    if (direct == null || reverse == null) {
      // TODO: throw correct exception
      throw new RuntimeException();
    }

    return Result.of(direct, reverse);
  }
}
