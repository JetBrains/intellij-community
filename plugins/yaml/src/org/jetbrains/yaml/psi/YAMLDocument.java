package org.jetbrains.yaml.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public interface YAMLDocument extends YAMLPsiElement {
  @Nullable
  YAMLValue getTopLevelValue();
}
