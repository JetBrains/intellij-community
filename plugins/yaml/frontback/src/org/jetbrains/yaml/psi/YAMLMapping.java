package org.jetbrains.yaml.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * A collection representing a set of key-value pairs
 */
public interface YAMLMapping extends YAMLCompoundValue {
  @NotNull
  Collection<YAMLKeyValue> getKeyValues();

  @Nullable
  YAMLKeyValue getKeyValueByKey(@NotNull String keyText);

  void putKeyValue(@NotNull YAMLKeyValue keyValueToAdd);

  /**
   * This one's different from plain deletion in a way that excess newlines/commas are also deleted
   */
  void deleteKeyValue(@NotNull YAMLKeyValue keyValueToDelete);
}
