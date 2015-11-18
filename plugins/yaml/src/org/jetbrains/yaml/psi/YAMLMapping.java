package org.jetbrains.yaml.psi;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface YAMLMapping extends YAMLCompoundValue {
  @NotNull
  List<YAMLKeyValue> getKeyValues();
}
