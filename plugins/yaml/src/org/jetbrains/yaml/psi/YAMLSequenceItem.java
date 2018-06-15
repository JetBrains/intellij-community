package org.jetbrains.yaml.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author oleg
 */
public interface YAMLSequenceItem extends YAMLPsiElement {
  @Nullable
  YAMLValue getValue();
  @NotNull
  Collection<YAMLKeyValue> getKeysValues();

  int getItemIndex();
}