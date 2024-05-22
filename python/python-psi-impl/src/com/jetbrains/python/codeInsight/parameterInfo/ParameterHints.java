package com.jetbrains.python.codeInsight.parameterInfo;

import com.intellij.codeInsight.parameterInfo.ParameterFlag;
import org.jetbrains.annotations.ApiStatus;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public final class ParameterHints {

  private final List<PyParameterInfoUtils.ParameterDescription> myParameterDescriptors;
  private final Map<Integer, EnumSet<ParameterFlag>> myFlags;

  public ParameterHints(List<PyParameterInfoUtils.ParameterDescription> parameterDescriptors, Map<Integer, EnumSet<ParameterFlag>> flags) {
    myParameterDescriptors = parameterDescriptors;
    myFlags = flags;
  }

  public List<PyParameterInfoUtils.ParameterDescription> getParameterDescriptors() {
    return myParameterDescriptors;
  }

  public Map<Integer, EnumSet<ParameterFlag>> getFlags() {
    return myFlags;
  }
}
