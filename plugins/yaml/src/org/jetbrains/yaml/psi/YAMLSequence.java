package org.jetbrains.yaml.psi;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A collection representing a sequence of items
 */
public interface YAMLSequence extends YAMLCompoundValue {
  @NotNull
  List<YAMLSequenceItem> getItems();
}
