package org.jetbrains.yaml.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public interface YAMLCompoundValue extends YAMLValue {
  @NotNull
  String getTextValue();
}