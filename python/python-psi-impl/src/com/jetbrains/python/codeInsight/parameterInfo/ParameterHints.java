package com.jetbrains.python.codeInsight.parameterInfo;

import com.intellij.codeInsight.parameterInfo.ParameterFlag;
import org.jetbrains.annotations.ApiStatus;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public final class ParameterHints {

  private final List<String> myHints;
  private final Map<Integer, EnumSet<ParameterFlag>> myFlags;

  public ParameterHints(List<String> hints, Map<Integer, EnumSet<ParameterFlag>> flags) {
    myHints = hints;
    myFlags = flags;
  }

  public List<String> getHints() {
    return myHints;
  }

  public Map<Integer, EnumSet<ParameterFlag>> getFlags() {
    return myFlags;
  }
}
