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
  private final List<String> myAnnotations;

  public ParameterHints(List<String> hints, Map<Integer, EnumSet<ParameterFlag>> flags, List<String> annotations) {
    myHints = hints;
    myFlags = flags;
    myAnnotations = annotations;
  }

  public List<String> getHints() {
    return myHints;
  }

  public Map<Integer, EnumSet<ParameterFlag>> getFlags() {
    return myFlags;
  }

  public List<String> getAnnotations() {
    return myAnnotations;
  }
}
